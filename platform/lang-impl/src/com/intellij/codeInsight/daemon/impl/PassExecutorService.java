// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Functions;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PassExecutorService implements Disposable {
  static final Logger LOG = Logger.getInstance(PassExecutorService.class);
  private static final boolean CHECK_CONSISTENCY = ApplicationManager.getApplication().isUnitTestMode();

  private final Map<ScheduledPass, Job<Void>> mySubmittedPasses = new ConcurrentHashMap<>();
  private final Project myProject;
  private final FileEditorManagerEx myFileEditorManager;
  private volatile boolean isDisposed;
  private final AtomicInteger nextAvailablePassId; // used to assign random id to a pass if not set

  PassExecutorService(@NotNull Project project) {
    myProject = project;
    nextAvailablePassId = ((TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(myProject)).getNextAvailableId();
    myFileEditorManager = (FileEditorManagerEx)FileEditorManager.getInstance(project);
  }

  @Override
  public void dispose() {
    cancelAll(true);
    // some workers could, although idle, still retain some thread references for some time causing leak hunter to frown
    ForkJoinPool.commonPool().awaitQuiescence(1, TimeUnit.SECONDS);
    isDisposed = true;
  }

  void cancelAll(boolean waitForTermination) {
    for (Map.Entry<ScheduledPass, Job<Void>> entry : mySubmittedPasses.entrySet()) {
      Job<Void> job = entry.getValue();
      ScheduledPass pass = entry.getKey();
      pass.myUpdateProgress.cancel();
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

  void submitPasses(@NotNull Map<FileEditor, HighlightingPass[]> passesMap,
                    // a list of opened FileEditors for each Document. The first FileEditor in the list is the preferred one
                    @NotNull Map<Document, List<FileEditor>> documentToEditors,
                    @NotNull DaemonProgressIndicator updateProgress) {
    if (isDisposed()) return;

    // null keys are ok
    Map<FileEditor, List<TextEditorHighlightingPass>> documentBoundPasses = new HashMap<>();
    MultiMap<FileEditor, EditorBoundHighlightingPass> editorBoundPasses = new MultiMap<>();
    Map<FileEditor, Int2ObjectMap<TextEditorHighlightingPass>> id2Pass = new HashMap<>();

    List<ScheduledPass> freePasses = new ArrayList<>(documentToEditors.size() * 5);
    AtomicInteger threadsToStartCountdown = new AtomicInteger(0);

    for (Map.Entry<FileEditor, HighlightingPass[]> entry : passesMap.entrySet()) {
      FileEditor fileEditor = entry.getKey();
      HighlightingPass[] passes = entry.getValue();

      for (HighlightingPass pass : passes) {
        Int2ObjectMap<TextEditorHighlightingPass> thisEditorId2Pass = id2Pass.computeIfAbsent(fileEditor, __ -> new Int2ObjectOpenHashMap<>(20));
        if (pass instanceof EditorBoundHighlightingPass) {
          EditorBoundHighlightingPass editorPass = (EditorBoundHighlightingPass)pass;
          int id = nextAvailablePassId.incrementAndGet();
          editorPass.setId(id); // have to make ids unique for this document
          checkUniquePassId(id, editorPass, thisEditorId2Pass);
          editorBoundPasses.putValue(fileEditor, editorPass);
        }
        else {
          TextEditorHighlightingPass tePass;
          if (pass instanceof TextEditorHighlightingPass) {
            tePass = (TextEditorHighlightingPass)pass;

            checkUniquePassId(tePass.getId(), tePass, thisEditorId2Pass);
            documentBoundPasses.computeIfAbsent(fileEditor, __->new ArrayList<>()).add(tePass);
          }
          else {
            // generic HighlightingPass, run all of them concurrently
            freePasses.add(new ScheduledPass(fileEditor, pass, updateProgress, threadsToStartCountdown));
          }
        }
      }
    }

    List<ScheduledPass> dependentPasses = new ArrayList<>(documentToEditors.size() * 10);
    // fileEditor-> (passId -> created pass)
    Map<FileEditor, Int2ObjectMap<ScheduledPass>> toBeSubmitted = new HashMap<>(passesMap.size());

    for (Map.Entry<Document, List<FileEditor>> entry : documentToEditors.entrySet()) {
      List<FileEditor> fileEditors = entry.getValue();
      FileEditor preferredFileEditor = fileEditors.get(0); // assumption: the preferred fileEditor is stored first
      List<TextEditorHighlightingPass> passes = documentBoundPasses.get(preferredFileEditor);
      if (passes == null || passes.isEmpty()) {
        continue;
      }
      sortById(passes);
      for (TextEditorHighlightingPass currentPass : passes) {
        createScheduledPass(preferredFileEditor, currentPass, toBeSubmitted, id2Pass, freePasses, dependentPasses, updateProgress,
                            threadsToStartCountdown);
      }
    }

    for (Map.Entry<FileEditor, Collection<EditorBoundHighlightingPass>> entry : editorBoundPasses.entrySet()) {
      FileEditor fileEditor = entry.getKey();
      Collection<EditorBoundHighlightingPass> createdEditorBoundPasses = entry.getValue();
      for (EditorBoundHighlightingPass pass : createdEditorBoundPasses) {
        createScheduledPass(fileEditor, pass, toBeSubmitted, id2Pass, freePasses, dependentPasses, updateProgress, threadsToStartCountdown);
      }
    }

    if (CHECK_CONSISTENCY && !ApplicationManagerEx.isInStressTest()) {
      assertConsistency(freePasses, toBeSubmitted, threadsToStartCountdown);
    }

    if (LOG.isDebugEnabled()) {
      Set<VirtualFile> vFiles = ContainerUtil.map2Set(passesMap.keySet(), FileEditor::getFile);

      log(updateProgress, null, vFiles + " ----- starting " + threadsToStartCountdown.get(), freePasses);
    }

    for (ScheduledPass dependentPass : dependentPasses) {
      mySubmittedPasses.put(dependentPass, Job.nullJob());
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private static void checkUniquePassId(int id,
                                        @NotNull TextEditorHighlightingPass pass,
                                        @NotNull Int2ObjectMap<TextEditorHighlightingPass> id2Pass) {
    TextEditorHighlightingPass prevPass = id2Pass.put(id, pass);
    if (prevPass != null) {
      LOG.error("Duplicate pass id found: "+id+". Both passes returned the same getId(): "+prevPass+" ("+prevPass.getClass() +") and "+pass+" ("+pass.getClass()+")");
    }
  }

  private void assertConsistency(@NotNull List<ScheduledPass> freePasses,
                                 @NotNull Map<FileEditor, Int2ObjectMap<ScheduledPass>> toBeSubmitted,
                                 @NotNull AtomicInteger threadsToStartCountdown) {
    assert threadsToStartCountdown.get() == toBeSubmitted.values().stream().mapToInt(m->m.size()).sum();
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
    assert id2Visits.size() == threadsToStartCountdown.get();
  }

  private void checkConsistency(@NotNull ScheduledPass pass, Map<ScheduledPass, Pair<ScheduledPass, Integer>> id2Visits) {
    for (ScheduledPass succ : ContainerUtil.concat(pass.mySuccessorsOnCompletion, pass.mySuccessorsOnSubmit)) {
      Pair<ScheduledPass, Integer> succPair = id2Visits.get(succ);
      if (succPair == null) {
        succPair = Pair.create(succ, succ.myRunningPredecessorsCount.get());
        id2Visits.put(succ, succPair);
      }
      int newPred = succPair.second - 1;
      id2Visits.put(succ, Pair.create(succ, newPred));
      assert newPred >= 0;
      if (newPred == 0) {
        checkConsistency(succ, id2Visits);
      }
    }
  }

  @NotNull
  private FileEditor getPreferredFileEditor(@NotNull Document document, @NotNull Collection<? extends FileEditor> fileEditors) {
    assert !fileEditors.isEmpty();
    FileEditor focusedEditor = ContainerUtil.find(fileEditors, it -> it instanceof TextEditor &&
                                                                     ((TextEditor)it).getEditor().getContentComponent().isFocusOwner());
    if (focusedEditor != null) return focusedEditor;

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      FileEditor selected = myFileEditorManager.getSelectedEditor(file);
      if (selected != null && fileEditors.contains(selected)) {
        return selected;
      }
    }
    return fileEditors.iterator().next();
  }

  @NotNull
  private ScheduledPass createScheduledPass(@NotNull FileEditor fileEditor,
                                            @NotNull TextEditorHighlightingPass pass,
                                            @NotNull Map<FileEditor, Int2ObjectMap<ScheduledPass>> toBeSubmitted,
                                            @NotNull Map<FileEditor, Int2ObjectMap<TextEditorHighlightingPass>> id2Pass,
                                            @NotNull List<ScheduledPass> freePasses,
                                            @NotNull List<ScheduledPass> dependentPasses,
                                            @NotNull DaemonProgressIndicator updateProgress,
                                            @NotNull AtomicInteger threadsToStartCountdown) {
    Int2ObjectMap<ScheduledPass> thisEditorId2ScheduledPass = toBeSubmitted.computeIfAbsent(fileEditor, __ -> new Int2ObjectOpenHashMap<>(20));
    Int2ObjectMap<TextEditorHighlightingPass> thisEditorId2Pass = id2Pass.computeIfAbsent(fileEditor, __ -> new Int2ObjectOpenHashMap<>(20));
    int passId = pass.getId();
    ScheduledPass scheduledPass = thisEditorId2ScheduledPass.get(passId);
    if (scheduledPass != null) return scheduledPass;
    scheduledPass = new ScheduledPass(fileEditor, pass, updateProgress, threadsToStartCountdown);
    threadsToStartCountdown.incrementAndGet();
    thisEditorId2ScheduledPass.put(passId, scheduledPass);
    for (int predecessorId : pass.getCompletionPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditor, toBeSubmitted, id2Pass, freePasses, dependentPasses,
                                                              updateProgress, threadsToStartCountdown, predecessorId,
                                                              thisEditorId2ScheduledPass, thisEditorId2Pass);
      if (predecessor != null) {
        predecessor.addSuccessorOnCompletion(scheduledPass);
      }
    }
    for (int predecessorId : pass.getStartingPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditor, toBeSubmitted, id2Pass, freePasses, dependentPasses,
                                                              updateProgress, threadsToStartCountdown, predecessorId,
                                                              thisEditorId2ScheduledPass, thisEditorId2Pass);
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

    if (pass.isRunIntentionPassAfter() && fileEditor instanceof TextEditor) {
      Editor editor = ((TextEditor)fileEditor).getEditor();
      VirtualFile virtualFile = fileEditor.getFile();
      PsiFile psiFile = virtualFile == null ? null : ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(virtualFile));
      if (psiFile != null) {
        ShowIntentionsPass ip = new ShowIntentionsPass(psiFile, editor, false);
        int id = nextAvailablePassId.incrementAndGet();
        ip.setId(id);
        checkUniquePassId(id, ip, thisEditorId2Pass);
        ip.setCompletionPredecessorIds(new int[]{passId});
        createScheduledPass(fileEditor, ip, toBeSubmitted, id2Pass, freePasses, dependentPasses, updateProgress, threadsToStartCountdown);
      }
    }

    return scheduledPass;
  }

  private ScheduledPass findOrCreatePredecessorPass(@NotNull FileEditor fileEditor,
                                                    @NotNull Map<FileEditor, Int2ObjectMap<ScheduledPass>> toBeSubmitted,
                                                    @NotNull Map<FileEditor, Int2ObjectMap<TextEditorHighlightingPass>> id2Pass,
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
      predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted,
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
    @NotNull private final DaemonProgressIndicator myUpdateProgress;

    private ScheduledPass(@NotNull FileEditor fileEditor,
                          @NotNull HighlightingPass pass,
                          @NotNull DaemonProgressIndicator progressIndicator,
                          @NotNull AtomicInteger threadsToStartCountdown) {
      myFileEditor = fileEditor;
      myPass = pass;
      myThreadsToStartCountdown = threadsToStartCountdown;
      myUpdateProgress = progressIndicator;
    }

    @Override
    public void run() {
      ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(() -> {
        try {
          doRun();
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

      log(myUpdateProgress, myPass, "Started. ");

      for (ScheduledPass successor : mySuccessorsOnSubmit) {
        int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
        if (predecessorsToRun == 0) {
          submit(successor);
        }
      }

      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        boolean success = ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> {
          try {
            if (DumbService.getInstance(myProject).isDumb() && !DumbService.isDumbAware(myPass)) {
              return;
            }

            if (!myUpdateProgress.isCanceled() && !myProject.isDisposed()) {
              myPass.collectInformation(myUpdateProgress);
            }
          }
          catch (ProcessCanceledException e) {
            log(myUpdateProgress, myPass, "Canceled ");

            if (!myUpdateProgress.isCanceled()) {
              myUpdateProgress.cancel(e); //in case when some smart asses throw PCE just for fun
            }
          }
          catch (RuntimeException | Error e) {
            myUpdateProgress.cancel(e);
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
        applyInformationToEditorsLater(myFileEditor, myPass, myUpdateProgress, myThreadsToStartCountdown, ()->{
          for (ScheduledPass successor : mySuccessorsOnCompletion) {
            int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
            if (predecessorsToRun == 0) {
              submit(successor);
            }
          }
        });
      }
    }

    @NonNls
    @Override
    public String toString() {
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
    ApplicationManager.getApplication().invokeLater(() -> {
      if (isDisposed() || !fileEditor.isValid()) {
        updateProgress.cancel();
      }
      if (updateProgress.isCanceled()) {
        log(updateProgress, pass, " is canceled during apply, sorry");
        return;
      }
      try {
        if (UIUtil.isShowing(fileEditor.getComponent())) {
          pass.applyInformationToEditor();
          repaintErrorStripeAndIcon(fileEditor);
          if (pass instanceof TextEditorHighlightingPass) {
            FileStatusMap fileStatusMap = DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap();
            Document document = ((TextEditorHighlightingPass)pass).getDocument();
            int passId = ((TextEditorHighlightingPass)pass).getId();
            fileStatusMap.markFileUpToDate(document, passId);
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
        String message = "Exception while applying information to " + fileEditor + "("+fileType+")";
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

  private void clearStaleEntries() {
    mySubmittedPasses.keySet().removeIf(pass -> pass.myUpdateProgress.isCanceled());
  }

  private void repaintErrorStripeAndIcon(@NotNull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      DefaultHighlightInfoProcessor.repaintErrorStripeAndIcon(((TextEditor)fileEditor).getEditor(), myProject);
    }
  }

  private boolean isDisposed() {
    return isDisposed || myProject.isDisposed();
  }

  @NotNull
  List<HighlightingPass> getAllSubmittedPasses() {
    List<HighlightingPass> result = new ArrayList<>(mySubmittedPasses.size());
    for (ScheduledPass scheduledPass : mySubmittedPasses.keySet()) {
      if (!scheduledPass.myUpdateProgress.isCanceled()) {
        result.add(scheduledPass.myPass);
      }
    }
    return result;
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
      Document document = pass instanceof TextEditorHighlightingPass ? ((TextEditorHighlightingPass)pass).getDocument() : null;
      CharSequence docText = document == null ? "" : ": '" + StringUtil.first(document.getCharsSequence(), 10, true)+ "'";
      synchronized (PassExecutorService.class) {
        String infos = StringUtil.join(info, Functions.TO_STRING(), " ");
        String message = StringUtil.repeatSymbol(' ', getThreadNum() * 4)
                         + " " + pass + " "
                         + infos
                         + "; progress=" + (progressIndicator == null ? null : progressIndicator.hashCode())
                         + " " + (progressIndicator == null ? "?" : progressIndicator.isCanceled() ? "X" : "V")
                         + docText;
        LOG.debug(message);
        //System.out.println(message);
      }
    }
  }

  private static final Key<Throwable> THROWABLE_KEY = Key.create("THROWABLE_KEY");
  private static void saveException(@NotNull Throwable e, @NotNull DaemonProgressIndicator indicator) {
    indicator.putUserDataIfAbsent(THROWABLE_KEY, e);
  }
  @TestOnly
  static Throwable getSavedException(@NotNull DaemonProgressIndicator indicator) {
    return indicator.getUserData(THROWABLE_KEY);
  }

  // return true if terminated
  boolean waitFor(int millis) throws Throwable {
    try {
      for (Job<Void> job : mySubmittedPasses.values()) {
        job.waitForCompletion(millis);
      }
      return true;
    }
    catch (TimeoutException ignored) {
      return false;
    }
    catch (InterruptedException e) {
      return true;
    }
    catch (ExecutionException e) {
      throw e.getCause();
    }
  }
}
