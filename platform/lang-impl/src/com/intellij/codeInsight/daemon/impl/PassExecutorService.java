// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ClientFileEditorManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Functions;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.ui.UIUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PassExecutorService implements Disposable {
  static final Logger LOG = Logger.getInstance(PassExecutorService.class);
  private static final boolean CHECK_CONSISTENCY = ApplicationManager.getApplication().isUnitTestMode();

  private final Map<ScheduledPass, Job<Void>> mySubmittedPasses = new ConcurrentHashMap<>();
  private final Project myProject;
  private volatile boolean isDisposed;

  PassExecutorService(@NotNull Project project) {
    myProject = project;
  }

  private int getNextAvailablePassId() {
    return ((TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(myProject)).getNextAvailableId();
  }

  @Override
  public void dispose() {
    cancelAll(true, "PassExecutorService.dispose");
    // some workers could, although idle, still retain some thread references for some time causing leak hunter to frown
    // call it from BGT to avoid "calling daemon from EDT" assertion
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ForkJoinPool.commonPool().awaitQuiescence(1, TimeUnit.SECONDS);
    });
    try {
      future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    isDisposed = true;
  }

  void cancelAll(boolean waitForTermination, @NotNull String reason) {
    for (Map.Entry<ScheduledPass, Job<Void>> entry : mySubmittedPasses.entrySet()) {
      Job<Void> job = entry.getValue();
      ScheduledPass pass = entry.getKey();
      pass.myUpdateProgress.cancel(reason);
      job.cancel();
    }
    try {
      if (waitForTermination) {
        while (!waitFor(50)) {
          int i = 0;
        }
      }
    }
    catch (ProcessCanceledException ignored) {
    }
    catch (Error | RuntimeException e) {
      throw e;
    }
    catch (Throwable throwable) {
      LOG.error(throwable);
    }
    finally {
      mySubmittedPasses.clear();
    }
  }

  void submitPasses(@NotNull Document document,
                    @NotNull VirtualFile virtualFile,
                    @NotNull PsiFile psiFile,
                    @NotNull FileEditor fileEditor,
                    HighlightingPass @NotNull [] passes,
                    @NotNull DaemonProgressIndicator updateProgress) {
    if (isDisposed()) {
      ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).stopMyProcess(updateProgress, true, "PES is disposed");
      return;
    }
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    List<TextEditorHighlightingPass> documentBoundPasses = new ArrayList<>();
    List<EditorBoundHighlightingPass> editorBoundPasses = new ArrayList<>();
    Int2ObjectMap<TextEditorHighlightingPass> id2Pass = new Int2ObjectOpenHashMap<>(30);

    List<ScheduledPass> freePasses = new ArrayList<>(); // passes free to start, with no "after" dependencies
    AtomicInteger threadsToStartCountdown = new AtomicInteger(0);

    for (HighlightingPass pass : passes) {
      if (pass instanceof EditorBoundHighlightingPass editorPass) {
        // have to make ids unique for this document
        assignUniqueId(editorPass, id2Pass);
        editorBoundPasses.add(editorPass);
      }
      else if (pass instanceof TextEditorHighlightingPass tePass) {
        assignUniqueId(tePass, id2Pass);
        documentBoundPasses.add(tePass);
      }
      else {
        // generic HighlightingPass, run all of them concurrently
        freePasses.add(new ScheduledPass(fileEditor, pass, updateProgress, threadsToStartCountdown));
      }
    }

    List<ScheduledPass> dependentPasses = new ArrayList<>();
    // passId -> created pass
    Int2ObjectMap<ScheduledPass> toBeSubmitted = new Int2ObjectOpenHashMap<>();
    sortById(documentBoundPasses);
    for (TextEditorHighlightingPass pass : documentBoundPasses) {
      createScheduledPass(fileEditor, document, virtualFile, psiFile, pass, toBeSubmitted, id2Pass, freePasses, dependentPasses, updateProgress, threadsToStartCountdown);
    }

    for (EditorBoundHighlightingPass pass : editorBoundPasses) {
      createScheduledPass(fileEditor, document, virtualFile, psiFile, pass, toBeSubmitted, id2Pass, freePasses, dependentPasses, updateProgress, threadsToStartCountdown);
    }

    if (CHECK_CONSISTENCY && !ApplicationManagerEx.isInStressTest()) {
      assertConsistency(freePasses, toBeSubmitted, threadsToStartCountdown);
    }

    if (LOG.isDebugEnabled()) {
      log(updateProgress, null, "submitPasses: "+virtualFile.getName() + " ----- starting " + threadsToStartCountdown.get() + "passes. free:"+freePasses+"; editorBound:"+editorBoundPasses+"; documentBound:"+documentBoundPasses);
    }

    for (ScheduledPass dependentPass : dependentPasses) {
      mySubmittedPasses.put(dependentPass, Job.nullJob());
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private void assignUniqueId(@NotNull TextEditorHighlightingPass pass, @NotNull Int2ObjectMap<TextEditorHighlightingPass> id2Pass) {
    int oldId = pass.getId();
    int id;
    if (oldId == -1 || oldId == 0) {
      id = getNextAvailablePassId();
      pass.setId(id);
    }
    else {
      id = oldId;
    }
    TextEditorHighlightingPass prevPass = id2Pass.put(id, pass);
    if (prevPass != null) {
      LOG.error("Duplicate pass id found: "+id+". Both passes returned the same getId(): "+prevPass+" ("+prevPass.getClass() +") and "+pass+" ("+pass.getClass()+"). oldId="+oldId);
    }
  }

  private void assertConsistency(@NotNull List<ScheduledPass> freePasses,
                                 @NotNull Int2ObjectMap<ScheduledPass> toBeSubmitted,
                                 @NotNull AtomicInteger threadsToStartCountdown) {
    assert threadsToStartCountdown.get() == toBeSubmitted.size();
    Map<ScheduledPass, Pair<ScheduledPass, Integer>> id2Visits = CollectionFactory.createCustomHashingStrategyMap(new HashingStrategy<>() {
      @Override
      public int hashCode(@Nullable PassExecutorService.ScheduledPass sp) {
        if (sp == null) return 0;
        return ((TextEditorHighlightingPass)sp.myPass).getId() * 31 + sp.myFileEditor.hashCode();
      }

      @Override
      public boolean equals(@Nullable PassExecutorService.ScheduledPass sp1, @Nullable PassExecutorService.ScheduledPass sp2) {
        if (sp1 == null || sp2 == null) return sp1 == sp2;
        int id1 = ((TextEditorHighlightingPass)sp1.myPass).getId();
        int id2 = ((TextEditorHighlightingPass)sp2.myPass).getId();
        return id1 == id2 && sp1.myFileEditor == sp2.myFileEditor;
      }
    });
    for (ScheduledPass freePass : freePasses) {
      HighlightingPass pass = freePass.myPass;
      if (pass instanceof TextEditorHighlightingPass) {
        id2Visits.put(freePass, Pair.create(freePass, 0));
        checkConsistency(freePass, id2Visits);
      }
    }
    for (Map.Entry<ScheduledPass, Pair<ScheduledPass, Integer>> entry : id2Visits.entrySet()) {
      int count = entry.getValue().second;
      assert count == 0 : entry.getKey();
    }
    assert id2Visits.size() == threadsToStartCountdown.get() : "Expected "+threadsToStartCountdown+" but got "+id2Visits.size()+": "+id2Visits;
  }

  private void checkConsistency(@NotNull ScheduledPass pass, Map<ScheduledPass, Pair<ScheduledPass, Integer>> id2Visits) {
    for (ScheduledPass successor : ContainerUtil.concat(pass.mySuccessorsOnCompletion, pass.mySuccessorsOnSubmit)) {
      Pair<ScheduledPass, Integer> pair = id2Visits.get(successor);
      if (pair == null) {
        pair = Pair.create(successor, successor.myRunningPredecessorsCount.get());
        id2Visits.put(successor, pair);
      }
      int newPred = pair.second - 1;
      id2Visits.put(successor, Pair.create(successor, newPred));
      assert newPred >= 0;
      if (newPred == 0) {
        checkConsistency(successor, id2Visits);
      }
    }
  }

  private @NotNull ScheduledPass createScheduledPass(@NotNull FileEditor fileEditor,
                                                     @NotNull Document document,
                                                     @NotNull VirtualFile virtualFile,
                                                     @NotNull PsiFile psiFile,
                                                     @NotNull TextEditorHighlightingPass pass,
                                                     @NotNull Int2ObjectMap<ScheduledPass> toBeSubmitted,
                                                     @NotNull Int2ObjectMap<TextEditorHighlightingPass> id2Pass,
                                                     @NotNull List<ScheduledPass> freePasses,
                                                     @NotNull List<ScheduledPass> dependentPasses,
                                                     @NotNull DaemonProgressIndicator updateProgress,
                                                     @NotNull AtomicInteger threadsToStartCountdown) {
    int passId = pass.getId();
    ScheduledPass scheduledPass = toBeSubmitted.get(passId);
    if (scheduledPass != null) return scheduledPass;
    scheduledPass = new ScheduledPass(fileEditor, pass, updateProgress, threadsToStartCountdown, Context.current());
    threadsToStartCountdown.incrementAndGet();
    toBeSubmitted.put(passId, scheduledPass);
    for (int predecessorId : pass.getCompletionPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditor, document, virtualFile, psiFile, toBeSubmitted, id2Pass, freePasses, dependentPasses,
                                                              updateProgress, threadsToStartCountdown, predecessorId,
                                                              toBeSubmitted, id2Pass);
      if (predecessor != null) {
        predecessor.addSuccessorOnCompletion(scheduledPass);
      }
    }
    for (int predecessorId : pass.getStartingPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditor, document, virtualFile, psiFile, toBeSubmitted, id2Pass, freePasses, dependentPasses,
                                                              updateProgress, threadsToStartCountdown, predecessorId,
                                                              toBeSubmitted, id2Pass);
      if (predecessor != null) {
        predecessor.addSuccessorOnSubmit(scheduledPass);
      }
    }
    if (scheduledPass.myRunningPredecessorsCount.get() == 0 && !freePasses.contains(scheduledPass)) {
      freePasses.add(scheduledPass);
    }
    else if (!dependentPasses.contains(scheduledPass)) {
      dependentPasses.add(scheduledPass);
    }

    if (pass.isRunIntentionPassAfter() && fileEditor instanceof TextEditor text) {
      Editor editor = text.getEditor();
      ShowIntentionsPass ip = new ShowIntentionsPass(psiFile, editor, false);
      assignUniqueId(ip, id2Pass);
      ip.setCompletionPredecessorIds(new int[]{passId});
      createScheduledPass(fileEditor, document, virtualFile, psiFile, ip, toBeSubmitted, id2Pass, freePasses, dependentPasses, updateProgress, threadsToStartCountdown);
    }

    return scheduledPass;
  }

  private ScheduledPass findOrCreatePredecessorPass(@NotNull FileEditor fileEditor,
                                                    @NotNull Document document,
                                                    @NotNull VirtualFile virtualFile,
                                                    @NotNull PsiFile psiFile,
                                                    @NotNull Int2ObjectMap<ScheduledPass> toBeSubmitted,
                                                    @NotNull Int2ObjectMap<TextEditorHighlightingPass> id2Pass,
                                                    @NotNull List<ScheduledPass> freePasses,
                                                    @NotNull List<ScheduledPass> dependentPasses,
                                                    @NotNull DaemonProgressIndicator updateProgress,
                                                    @NotNull AtomicInteger myThreadsToStartCountdown,
                                                    int predecessorId,
                                                    @NotNull Int2ObjectMap<ScheduledPass> thisEditorId2ScheduledPass,
                                                    @NotNull Int2ObjectMap<? extends TextEditorHighlightingPass> thisEditorId2Pass) {
    ScheduledPass predecessor = thisEditorId2ScheduledPass.get(predecessorId);
    if (predecessor == null) {
      TextEditorHighlightingPass textEditorPass = thisEditorId2Pass.get(predecessorId);
      predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, document, virtualFile, psiFile, textEditorPass, toBeSubmitted,
                                                                        id2Pass, freePasses,
                                                                        dependentPasses, updateProgress, myThreadsToStartCountdown);
    }
    return predecessor;
  }

  private void submit(@NotNull ScheduledPass pass) {
    if (!pass.myUpdateProgress.isCanceled()) {
      Job<Void> job = JobLauncher.getInstance().submitToJobThread(pass, future -> {
        try {
          if (!future.isCancelled()) { // for canceled task .get() generates CancellationException which is expensive
            future.get();
          }
        }
        catch (CancellationException | InterruptedException ignored) {
        }
        catch (ExecutionException e) {
          LOG.error(e.getCause());
        }
      });
      mySubmittedPasses.put(pass, job);
    }
  }

  private final class ScheduledPass implements Runnable {
    private final FileEditor myFileEditor;
    private final HighlightingPass myPass;
    private final AtomicInteger myThreadsToStartCountdown;
    private final AtomicInteger myRunningPredecessorsCount = new AtomicInteger(0);
    private final List<ScheduledPass> mySuccessorsOnCompletion = new ArrayList<>();
    private final List<ScheduledPass> mySuccessorsOnSubmit = new ArrayList<>();
    private final @NotNull DaemonProgressIndicator myUpdateProgress;
    private final @NotNull Context myOpenTelemetryContext;

    private ScheduledPass(@NotNull FileEditor fileEditor,
                          @NotNull HighlightingPass pass,
                          @NotNull DaemonProgressIndicator progressIndicator,
                          @NotNull AtomicInteger threadsToStartCountdown) {
      this(fileEditor, pass, progressIndicator, threadsToStartCountdown,  Context.current());
    }

    private ScheduledPass(@NotNull FileEditor fileEditor,
                          @NotNull HighlightingPass pass,
                          @NotNull DaemonProgressIndicator progressIndicator,
                          @NotNull AtomicInteger threadsToStartCountdown,
                          @NotNull Context openTelemetryContext) {
      myFileEditor = fileEditor;
      myPass = pass;
      myThreadsToStartCountdown = threadsToStartCountdown;
      myUpdateProgress = progressIndicator;
      myOpenTelemetryContext = openTelemetryContext;
    }

    @Override
    public void run() {
      ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(() -> {
        try {
          ((FileTypeManagerImpl)FileTypeManager.getInstance()).cacheFileTypesInside(() -> doRun());
        }
        catch (ApplicationUtil.CannotRunReadActionException e) {
          myUpdateProgress.cancel();
        }
        catch (RuntimeException | Error e) {
          saveException(e, myUpdateProgress);
          throw e;
        }
      });
    }

    private void doRun() {
      if (myUpdateProgress.isCanceled()) return;

      log(myUpdateProgress, myPass, "Started.");

      for (ScheduledPass successor : mySuccessorsOnSubmit) {
        int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
        if (predecessorsToRun == 0) {
          submit(successor);
        }
      }

      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        boolean success = ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> {
          try {
            if (!DumbService.getInstance(myProject).isUsableInCurrentContext(myPass)) {
              return;
            }

            if (!myUpdateProgress.isCanceled() && !myProject.isDisposed()) {
              String fileName = myFileEditor.getFile().getName();
              String passClassName = StringUtil.getShortName(myPass.getClass());
              try (Scope __ = myOpenTelemetryContext.makeCurrent()) {
                TraceKt.use(HighlightingPassTracer.HIGHLIGHTING_PASS_TRACER.spanBuilder(passClassName), span -> {
                  Activity startupActivity = StartUpMeasurer.startActivity("running " + passClassName);
                  boolean cancelled = false;
                  try (AccessToken ignored = ClientId.withClientId(ClientFileEditorManager.getClientId(myFileEditor))) {
                    myPass.collectInformation(myUpdateProgress);
                  }
                  catch (CancellationException e) {
                    cancelled = true;
                    throw e;
                  }
                  finally {
                    startupActivity.end();
                    span.setAttribute(HighlightingPassTracer.FILE_ATTR_SPAN_KEY, fileName);
                    span.setAttribute(HighlightingPassTracer.CANCELLED_ATTR_SPAN_KEY, Boolean.toString(cancelled));
                  }
                  return null;
                });
              }
            }
          }
          catch (ProcessCanceledException e) {
            log(myUpdateProgress, myPass, "Canceled ");
            if (!myUpdateProgress.isCanceled()) {
              //in case some smart asses throw PCE just for fun
              ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).stopMyProcess(myUpdateProgress, true, "PCE was thrown by pass");
              if (LOG.isDebugEnabled()) {
                LOG.debug("PCE was thrown by " + myPass.getClass(), e);
              }
            }
          }
          catch (RuntimeException | Error e) {
            myUpdateProgress.cancel(e, "Error occurred");
            LOG.error(e);
            throw e;
          }
        });

        if (!success) {
          myUpdateProgress.cancel();
        }
      }, myUpdateProgress);

      log(myUpdateProgress, myPass, "Finished. ");

      if (!myUpdateProgress.isCanceled()) {
        applyInformationToEditorsLater(myFileEditor, myPass, myUpdateProgress, myThreadsToStartCountdown, ()-> {
          for (ScheduledPass successor : mySuccessorsOnCompletion) {
            int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
            if (predecessorsToRun == 0) {
              submit(successor);
            }
          }
        });
      }
    }

    @Override
    public @NonNls String toString() {
      return "SP: " + myPass;
    }

    private void addSuccessorOnCompletion(@NotNull ScheduledPass successor) {
      mySuccessorsOnCompletion.add(successor);
      successor.myRunningPredecessorsCount.incrementAndGet();
    }

    private void addSuccessorOnSubmit(@NotNull ScheduledPass successor) {
      mySuccessorsOnSubmit.add(successor);
      successor.myRunningPredecessorsCount.incrementAndGet();
    }
  }

  private void applyInformationToEditorsLater(@NotNull FileEditor fileEditor,
                                              @NotNull HighlightingPass pass,
                                              @NotNull DaemonProgressIndicator updateProgress,
                                              @NotNull AtomicInteger threadsToStartCountdown,
                                              @NotNull Runnable callbackOnApplied) {
    try {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (isDisposed() || !fileEditor.isValid()) {
          updateProgress.cancel();
        }
        if (updateProgress.isCanceled()) {
          log(updateProgress, pass, " is canceled during apply, sorry");
          return;
        }
        try (AccessToken ignored = ClientId.withClientId(ClientFileEditorManager.getClientId(fileEditor))) {
          if (UIUtil.isShowing(fileEditor.getComponent())) {
            pass.applyInformationToEditor();
            repaintErrorStripeAndIcon(fileEditor);
            if (pass instanceof TextEditorHighlightingPass text) {
              text.markUpToDateIfStillValid();
            }
            log(updateProgress, pass, " Applied");
          }
        }
        catch (ProcessCanceledException e) {
          log(updateProgress, pass, "Error " + e);
          throw e;
        }
        catch (RuntimeException e) {
          VirtualFile file = fileEditor.getFile();
          FileType fileType = file == null ? null : file.getFileType();
          String message = "Exception while applying information to " + fileEditor + "(" + fileType + ")";
          log(updateProgress, pass, message + e);
          throw new RuntimeException(message, e);
        }
        if (threadsToStartCountdown.decrementAndGet() == 0) {
          HighlightingSessionImpl.waitForAllSessionsHighlightInfosApplied(updateProgress);
          log(updateProgress, pass, "Stopping ");
          updateProgress.stopIfRunning();
          clearStaleEntries();
        }
        else {
          log(updateProgress, pass, "Finished but there are passes in the queue: " + threadsToStartCountdown.get());
        }
        callbackOnApplied.run();
      }, updateProgress.getModalityState(), pass.getExpiredCondition());
    }
    catch (ProcessCanceledException ignored) {
      // pass.getExpiredCondition() computation could throw PCE
    }
  }

  private void clearStaleEntries() {
    mySubmittedPasses.keySet().removeIf(pass -> pass.myUpdateProgress.isCanceled());
  }

  private void repaintErrorStripeAndIcon(@NotNull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor textEditor) {
      Editor editor = textEditor.getEditor();
      DaemonCodeAnalyzerImpl.repaintErrorStripeAndIcon(editor, myProject,
                                                       PsiDocumentManager.getInstance(myProject).getCachedPsiFile(editor.getDocument()));
    }
  }

  private boolean isDisposed() {
    return isDisposed || myProject.isDisposed();
  }

  @NotNull
  List<HighlightingPass> getAllSubmittedPasses() {
    return ContainerUtil.mapNotNull(mySubmittedPasses.keySet(),
                                    scheduledPass -> scheduledPass.myUpdateProgress.isCanceled() ? null : scheduledPass.myPass);
  }

  private static void sortById(@NotNull List<? extends TextEditorHighlightingPass> result) {
    ContainerUtil.quickSort(result, Comparator.comparingInt(TextEditorHighlightingPass::getId));
  }

  private static int getThreadNum() {
    Matcher matcher = Pattern.compile("JobScheduler FJ pool (\\d*)/(\\d*)").matcher(Thread.currentThread().getName());
    String num = matcher.matches() ? matcher.group(1) : null;
    return StringUtil.parseInt(num, 0);
  }

  static void log(ProgressIndicator progressIndicator, HighlightingPass pass, @NonNls Object @NotNull ... info) {
    if (LOG.isDebugEnabled()) {
      Document document = pass instanceof TextEditorHighlightingPass text ? text.getDocument() : null;
      CharSequence docText = document == null ? "" : ": '" + StringUtil.first(document.getCharsSequence(), 10, true)+ "'";
      synchronized (PassExecutorService.class) {
        String message = StringUtil.repeatSymbol(' ', getThreadNum() * 4)
                         + " " + (pass == null ? "" : pass + " ")
                         + StringUtil.join(info, Functions.TO_STRING(), " ")
                         + "; progress=" + (progressIndicator == null ? null : System.identityHashCode(progressIndicator))
                         + (progressIndicator == null ? "?" : progressIndicator.isCanceled() ? "X" : "V")
                         + " " + docText;
        LOG.debug(message);
      }
    }
  }

  private static final Key<Throwable> THROWABLE_KEY = Key.create("THROWABLE_KEY");
  static void saveException(@NotNull Throwable e, @NotNull DaemonProgressIndicator indicator) {
    indicator.putUserDataIfAbsent(THROWABLE_KEY, e);
  }
  @TestOnly
  static Throwable getSavedException(@NotNull DaemonProgressIndicator indicator) {
    return indicator.getUserData(THROWABLE_KEY);
  }

  // return true if terminated
  boolean waitFor(long millis) {
    long deadline = System.currentTimeMillis() + millis;
    try {
      for (Job<Void> job : mySubmittedPasses.values()) {
        if (!job.waitForCompletion((int)(System.currentTimeMillis() - deadline))) {
          return false;
        }
      }
      return true;
    }
    catch (InterruptedException e) {
      return true;
    }
  }
}
