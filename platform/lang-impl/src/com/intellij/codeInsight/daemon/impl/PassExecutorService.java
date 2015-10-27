/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdr
 */
class PassExecutorService implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PassExecutorService");
  private static final boolean CHECK_CONSISTENCY = ApplicationManager.getApplication().isUnitTestMode();

  private final Map<ScheduledPass, Job<Void>> mySubmittedPasses = new ConcurrentHashMap<ScheduledPass, Job<Void>>();
  private final Project myProject;
  private volatile boolean isDisposed;
  private final AtomicInteger nextPassId = new AtomicInteger(100);

  PassExecutorService(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
    cancelAll(true);
    isDisposed = true;
  }

  void cancelAll(boolean waitForTermination) {
    for (Job<Void> submittedPass : mySubmittedPasses.values()) {
      submittedPass.cancel();
    }
    if (waitForTermination) {
      try {
        while (!waitFor(50)) {
          int i = 0;
        }
      }
      catch (ProcessCanceledException ignored) {

      }
      catch (Error e) {
        throw e;
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Throwable throwable) {
        LOG.error(throwable);
      }
    }
    mySubmittedPasses.clear();
  }

  void submitPasses(@NotNull Map<FileEditor, HighlightingPass[]> passesMap, @NotNull DaemonProgressIndicator updateProgress) {
    if (isDisposed()) return;

    // null keys are ok
    MultiMap<Document, FileEditor> documentToEditors = MultiMap.createSet();
    MultiMap<FileEditor, TextEditorHighlightingPass> documentBoundPasses = MultiMap.createSmart();
    MultiMap<FileEditor, EditorBoundHighlightingPass> editorBoundPasses = MultiMap.createSmart();
    Set<VirtualFile> vFiles = new HashSet<VirtualFile>();

    for (Map.Entry<FileEditor, HighlightingPass[]> entry : passesMap.entrySet()) {
      FileEditor fileEditor = entry.getKey();
      HighlightingPass[] passes = entry.getValue();
      Document document;
      if (fileEditor instanceof TextEditor) {
        Editor editor = ((TextEditor)fileEditor).getEditor();
        LOG.assertTrue(!(editor instanceof EditorWindow));
        document = editor.getDocument();
      }
      else {
        VirtualFile virtualFile = ((FileEditorManagerEx)FileEditorManager.getInstance(myProject)).getFile(fileEditor);
        document = virtualFile == null ? null : FileDocumentManager.getInstance().getDocument(virtualFile);
      }
      if (document != null) {
        vFiles.add(FileDocumentManager.getInstance().getFile(document));
      }

      int prevId = 0;
      for (final HighlightingPass pass : passes) {
        if (pass instanceof EditorBoundHighlightingPass) {
          EditorBoundHighlightingPass editorPass = (EditorBoundHighlightingPass)pass;
          editorPass.setId(nextPassId.incrementAndGet()); // have to make ids unique for this document
          editorBoundPasses.putValue(fileEditor, editorPass);
        }
        else {
          TextEditorHighlightingPass textEditorHighlightingPass = convertToTextHighlightingPass(pass, document, nextPassId, prevId);
          document = textEditorHighlightingPass.getDocument();
          documentBoundPasses.putValue(fileEditor, textEditorHighlightingPass);
          if (document != null) {
            documentToEditors.putValue(document, fileEditor);
          }
          prevId = textEditorHighlightingPass.getId();
        }
      }
    }

    List<ScheduledPass> freePasses = new ArrayList<ScheduledPass>(documentToEditors.size()*5);
    List<ScheduledPass> dependentPasses = new ArrayList<ScheduledPass>(documentToEditors.size()*10);
    // (fileEditor, passId) -> created pass
    Map<Pair<FileEditor, Integer>, ScheduledPass> toBeSubmitted = new THashMap<Pair<FileEditor, Integer>, ScheduledPass>(passesMap.size());

    final AtomicInteger threadsToStartCountdown = new AtomicInteger(0);
    for (Map.Entry<Document, Collection<FileEditor>> entry : documentToEditors.entrySet()) {
      Collection<FileEditor> fileEditors = entry.getValue();
      Document document = entry.getKey();
      FileEditor preferredFileEditor = getPreferredFileEditor(document, fileEditors);
      List<TextEditorHighlightingPass> passes = (List<TextEditorHighlightingPass>)documentBoundPasses.get(preferredFileEditor);
      if (passes.isEmpty()) {
        continue;
      }
      sortById(passes);
      for (TextEditorHighlightingPass currentPass : passes) {
        createScheduledPass(preferredFileEditor, currentPass, toBeSubmitted, passes, freePasses, dependentPasses, updateProgress, threadsToStartCountdown);
      }
    }

    for (Map.Entry<FileEditor, Collection<EditorBoundHighlightingPass>> entry : editorBoundPasses.entrySet()) {
      FileEditor fileEditor = entry.getKey();
      Collection<EditorBoundHighlightingPass> createdEditorBoundPasses = entry.getValue();
      List<TextEditorHighlightingPass> createdDocumentBoundPasses = (List<TextEditorHighlightingPass>)documentBoundPasses.get(fileEditor);
      List<TextEditorHighlightingPass> allCreatedPasses = new ArrayList<TextEditorHighlightingPass>(createdDocumentBoundPasses);
      allCreatedPasses.addAll(createdEditorBoundPasses);

      for (EditorBoundHighlightingPass pass : createdEditorBoundPasses) {
        createScheduledPass(fileEditor, pass, toBeSubmitted, allCreatedPasses, freePasses, dependentPasses, updateProgress, threadsToStartCountdown);
      }
    }

    if (CHECK_CONSISTENCY && !ApplicationInfoImpl.isInPerformanceTest()) {
      assertConsistency(freePasses, toBeSubmitted, threadsToStartCountdown);
    }

    log(updateProgress, null, vFiles + " ----- starting " + threadsToStartCountdown.get(), freePasses);

    for (ScheduledPass dependentPass : dependentPasses) {
      mySubmittedPasses.put(dependentPass, Job.NULL_JOB);
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private void assertConsistency(List<ScheduledPass> freePasses,
                                   Map<Pair<FileEditor, Integer>, ScheduledPass> toBeSubmitted,
                                   AtomicInteger threadsToStartCountdown) {
    assert threadsToStartCountdown.get() == toBeSubmitted.size();
    TIntObjectHashMap<Pair<ScheduledPass, Integer>> id2Visits = new TIntObjectHashMap<Pair<ScheduledPass, Integer>>();
    for (ScheduledPass freePass : freePasses) {
      id2Visits.put(freePass.myPass.getId(), Pair.create(freePass, 0));
      checkConsistency(freePass, id2Visits);
    }
    id2Visits.forEachEntry(new TIntObjectProcedure<Pair<ScheduledPass,Integer>>() {
      @Override
      public boolean execute(int id, Pair<ScheduledPass, Integer> pair) {
        int count = pair.second;
        assert count == 0 : id;
        return true;
      }
    });
    assert id2Visits.size() == threadsToStartCountdown.get();
  }

  private void checkConsistency(ScheduledPass pass, TIntObjectHashMap<Pair<ScheduledPass, Integer>> id2Visits) {
    for (ScheduledPass succ : ContainerUtil.concat(pass.mySuccessorsOnCompletion, pass.mySuccessorsOnSubmit)) {
      int succId = succ.myPass.getId();
      Pair<ScheduledPass, Integer> succPair = id2Visits.get(succId);
      if (succPair == null) {
        succPair = Pair.create(succ, succ.myRunningPredecessorsCount.get());
        id2Visits.put(succId, succPair);
      }
      int newPred = succPair.second - 1;
      id2Visits.put(succId, Pair.create(succ, newPred));
      assert newPred >= 0;
      if (newPred == 0) {
        checkConsistency(succ, id2Visits);
      }
    }
  }

  @NotNull
  private TextEditorHighlightingPass convertToTextHighlightingPass(@NotNull final HighlightingPass pass,
                                                                   final Document document,
                                                                   @NotNull AtomicInteger id,
                                                                   int previousPassId) {
    TextEditorHighlightingPass textEditorHighlightingPass;
    if (pass instanceof TextEditorHighlightingPass) {
      textEditorHighlightingPass = (TextEditorHighlightingPass)pass;
    }
    else {
      // run all passes in sequence
      textEditorHighlightingPass = new TextEditorHighlightingPass(myProject, document, true) {
        @Override
        public void doCollectInformation(@NotNull ProgressIndicator progress) {
          pass.collectInformation(progress);
        }

        @Override
        public void doApplyInformationToEditor() {
          pass.applyInformationToEditor();
        }
      };
      textEditorHighlightingPass.setId(id.incrementAndGet());
      if (previousPassId != 0) {
        textEditorHighlightingPass.setCompletionPredecessorIds(new int[]{previousPassId});
      }
    }
    return textEditorHighlightingPass;
  }

  @NotNull
  private FileEditor getPreferredFileEditor(Document document, @NotNull Collection<FileEditor> fileEditors) {
    assert !fileEditors.isEmpty();
    if (document != null) {
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file != null) {
        final FileEditor selected = FileEditorManager.getInstance(myProject).getSelectedEditor(file);
        if (selected != null && fileEditors.contains(selected)) {
          return selected;
        }
      }
    }
    return fileEditors.iterator().next();
  }

  @NotNull
  private ScheduledPass createScheduledPass(@NotNull FileEditor fileEditor,
                                            @NotNull TextEditorHighlightingPass pass,
                                            @NotNull Map<Pair<FileEditor, Integer>, ScheduledPass> toBeSubmitted,
                                            @NotNull List<TextEditorHighlightingPass> textEditorHighlightingPasses,
                                            @NotNull List<ScheduledPass> freePasses,
                                            @NotNull List<ScheduledPass> dependentPasses,
                                            @NotNull DaemonProgressIndicator updateProgress,
                                            @NotNull AtomicInteger threadsToStartCountdown) {
    int passId = pass.getId();
    Pair<FileEditor, Integer> key = Pair.create(fileEditor, passId);
    ScheduledPass scheduledPass = toBeSubmitted.get(key);
    if (scheduledPass != null) return scheduledPass;
    scheduledPass = new ScheduledPass(fileEditor, pass, updateProgress, threadsToStartCountdown);
    threadsToStartCountdown.incrementAndGet();
    toBeSubmitted.put(key, scheduledPass);
    for (int predecessorId : pass.getCompletionPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditor, toBeSubmitted, textEditorHighlightingPasses, freePasses, dependentPasses,
                                                              updateProgress, threadsToStartCountdown, predecessorId);
      if (predecessor != null) {
        predecessor.addSuccessorOnCompletion(scheduledPass);
      }
    }
    for (int predecessorId : pass.getStartingPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditor, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                              dependentPasses, updateProgress, threadsToStartCountdown,
                                                              predecessorId);
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
      ShowIntentionsPass ip = new ShowIntentionsPass(myProject, editor, -1);
      ip.setId(nextPassId.incrementAndGet());
      ip.setCompletionPredecessorIds(new int[]{scheduledPass.myPass.getId()});

      createScheduledPass(fileEditor, ip, toBeSubmitted, textEditorHighlightingPasses, freePasses, dependentPasses, updateProgress, threadsToStartCountdown);
    }

    return scheduledPass;
  }

  private ScheduledPass findOrCreatePredecessorPass(@NotNull FileEditor fileEditor,
                                                    @NotNull Map<Pair<FileEditor, Integer>, ScheduledPass> toBeSubmitted,
                                                    @NotNull List<TextEditorHighlightingPass> textEditorHighlightingPasses,
                                                    @NotNull List<ScheduledPass> freePasses,
                                                    @NotNull List<ScheduledPass> dependentPasses,
                                                    @NotNull DaemonProgressIndicator updateProgress,
                                                    @NotNull AtomicInteger myThreadsToStartCountdown,
                                                    final int predecessorId) {
    Pair<FileEditor, Integer> predKey = Pair.create(fileEditor, predecessorId);
    ScheduledPass predecessor = toBeSubmitted.get(predKey);
    if (predecessor == null) {
      TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
      predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                                        dependentPasses, updateProgress, myThreadsToStartCountdown);
    }
    return predecessor;
  }

  private static TextEditorHighlightingPass findPassById(final int id, @NotNull List<TextEditorHighlightingPass> textEditorHighlightingPasses) {
    return ContainerUtil.find(textEditorHighlightingPasses, new Condition<TextEditorHighlightingPass>() {
      @Override
      public boolean value(TextEditorHighlightingPass pass) {
        return pass.getId() == id;
      }
    });
  }

  private void submit(@NotNull ScheduledPass pass) {
    if (!pass.myUpdateProgress.isCanceled()) {
      Job<Void> job = JobLauncher.getInstance().submitToJobThread(pass, new Consumer<Future>() {
        @Override
        public void consume(Future future) {
          try {
            if (!future.isCancelled()) { // for canceled task .get() generates CancellationException which is expensive
              future.get();
            }
          }
          catch (CancellationException ignored) {
          }
          catch (InterruptedException ignored) {
          }
          catch (ExecutionException e) {
            LOG.error(e.getCause());
          }
        }
      });
      mySubmittedPasses.put(pass, job);
    }
  }

  private class ScheduledPass implements Runnable {
    private final FileEditor myFileEditor;
    private final TextEditorHighlightingPass myPass;
    private final AtomicInteger myThreadsToStartCountdown;
    private final AtomicInteger myRunningPredecessorsCount = new AtomicInteger(0);
    private final Collection<ScheduledPass> mySuccessorsOnCompletion = new ArrayList<ScheduledPass>();
    private final Collection<ScheduledPass> mySuccessorsOnSubmit = new ArrayList<ScheduledPass>();
    private final DaemonProgressIndicator myUpdateProgress;

    private ScheduledPass(@NotNull FileEditor fileEditor,
                          @NotNull TextEditorHighlightingPass pass,
                          @NotNull DaemonProgressIndicator progressIndicator,
                          @NotNull AtomicInteger threadsToStartCountdown) {
      myFileEditor = fileEditor;
      myPass = pass;
      myThreadsToStartCountdown = threadsToStartCountdown;
      myUpdateProgress = progressIndicator;
    }

    @Override
    public void run() {
      try {
        doRun();
      }
      catch (RuntimeException e) {
        saveException(e,myUpdateProgress);
        throw e;
      }
      catch (Error e) {
        saveException(e,myUpdateProgress);
        throw e;
      }
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

      ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
        @Override
        public void run() {
          boolean success = ApplicationManagerEx.getApplicationEx().tryRunReadAction(new Runnable() {
            @Override
            public void run() {
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
              catch (RuntimeException e) {
                myUpdateProgress.cancel(e);
                LOG.error(e);
                throw e;
              }
              catch (Error e) {
                myUpdateProgress.cancel(e);
                LOG.error(e);
                throw e;
              }
            }
          });

          if (!success) {
            myUpdateProgress.cancel();
          }
        }
      }, myUpdateProgress);

      log(myUpdateProgress, myPass, "Finished. ");

      if (!myUpdateProgress.isCanceled()) {
        applyInformationToEditorsLater(myFileEditor, myPass, myUpdateProgress, myThreadsToStartCountdown);
        for (ScheduledPass successor : mySuccessorsOnCompletion) {
          int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
          if (predecessorsToRun == 0) {
            submit(successor);
          }
        }
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

  private void applyInformationToEditorsLater(@NotNull final FileEditor fileEditor,
                                              @NotNull final TextEditorHighlightingPass pass,
                                              @NotNull final DaemonProgressIndicator updateProgress,
                                              @NotNull final AtomicInteger threadsToStartCountdown) {
    ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
      @Override
      public void run() {
        if (isDisposed() || myProject.isDisposed()) {
          updateProgress.cancel();
        }
        if (updateProgress.isCanceled()) {
          log(updateProgress, pass, " is canceled during apply, sorry");
          return;
        }
        try {
          if (fileEditor.getComponent().isDisplayable() || ApplicationManager.getApplication().isUnitTestMode()) {
            log(updateProgress, pass, " Applied");
            pass.applyInformationToEditor();
          }
        }
        catch (RuntimeException e) {
          log(updateProgress, pass, "Error " + e);
          throw e;
        }
        if (threadsToStartCountdown.decrementAndGet() == 0) {
          log(updateProgress, pass, "Stopping ");
          updateProgress.stopIfRunning();
        }
        else {
          log(updateProgress, pass, "Finished but there are passes in the queue: " + threadsToStartCountdown.get());
        }
      }
    }, ModalityState.stateForComponent(fileEditor.getComponent()));
  }

  protected boolean isDisposed() {
    return isDisposed;
  }

  @NotNull
  List<TextEditorHighlightingPass> getAllSubmittedPasses() {
    List<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(mySubmittedPasses.size());
    for (ScheduledPass scheduledPass : mySubmittedPasses.keySet()) {
      if (!scheduledPass.myUpdateProgress.isCanceled()) {
        result.add(scheduledPass.myPass);
      }
    }
    sortById(result);
    return result;
  }

  private static void sortById(@NotNull List<TextEditorHighlightingPass> result) {
    ContainerUtil.quickSort(result, new Comparator<TextEditorHighlightingPass>() {
      @Override
      public int compare(@NotNull TextEditorHighlightingPass o1, @NotNull TextEditorHighlightingPass o2) {
        return o1.getId() - o2.getId();
      }
    });
  }

  private static int getThreadNum() {
    Matcher matcher = Pattern.compile("JobScheduler FJ pool (\\d*)/(\\d*)").matcher(Thread.currentThread().getName());
    String num = matcher.matches() ? matcher.group(1) : null;
    return StringUtil.parseInt(num, 0);
  }

  static void log(ProgressIndicator progressIndicator, TextEditorHighlightingPass pass, @NonNls @NotNull Object... info) {
    if (LOG.isDebugEnabled()) {
      CharSequence docText = pass == null ? "" : StringUtil.first(pass.getDocument().getCharsSequence(), 10, true);
      synchronized (PassExecutorService.class) {
        StringBuilder s = new StringBuilder();
        for (Object o : info) {
          s.append(o).append(" ");
        }
        String message = StringUtil.repeatSymbol(' ', getThreadNum() * 4)
                         + " " + pass + " "
                         + s
                         + "; progress=" + (progressIndicator == null ? null : progressIndicator.hashCode())
                         + " " + (progressIndicator == null ? "?" : progressIndicator.isCanceled() ? "X" : "V")
                         + " : '" + docText + "'";
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
