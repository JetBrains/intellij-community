/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author cdr
 */
public abstract class PassExecutorService implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PassExecutorService");

  private final Map<ScheduledPass, Job<Void>> mySubmittedPasses = new ConcurrentHashMap<ScheduledPass, Job<Void>>();
  private final Project myProject;
  private volatile boolean isDisposed;
  private final AtomicInteger nextPassId = new AtomicInteger(100);

  public PassExecutorService(Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
    cancelAll(true);
    isDisposed = true;
  }

  public void cancelAll(boolean waitForTermination) {
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
      catch (Throwable throwable) {
        LOG.error(throwable);
      }
    }
    mySubmittedPasses.clear();
  }

  public void submitPasses(@NotNull Map<FileEditor, HighlightingPass[]> passesMap, @NotNull DaemonProgressIndicator updateProgress, final int jobPriority) {
    if (isDisposed()) return;
    int id = 1;

    // (doc, passId) -> created pass
    Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted = new THashMap<Pair<Document, Integer>, ScheduledPass>(passesMap.size());
    // null keys are ok
    Map<Document, List<FileEditor>> documentToEditors = new HashMap<Document, List<FileEditor>>();
    Map<FileEditor, List<TextEditorHighlightingPass>> textPasses = new HashMap<FileEditor, List<TextEditorHighlightingPass>>(passesMap.size());
    for (Map.Entry<FileEditor, HighlightingPass[]> entry : passesMap.entrySet()) {
      FileEditor fileEditor = entry.getKey();
      HighlightingPass[] passes = entry.getValue();
      Document document = null;
      if (fileEditor instanceof TextEditor) {
        Editor editor = ((TextEditor)fileEditor).getEditor();
        LOG.assertTrue(!(editor instanceof EditorWindow));
        document = editor.getDocument();
      }

      for (int i = 0; i < passes.length; i++) {
        final HighlightingPass pass = passes[i];

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
          textEditorHighlightingPass.setId(id++);
          if (i > 0) {
            textEditorHighlightingPass.setCompletionPredecessorIds(new int[]{i - 1});
          }
        }
        document = textEditorHighlightingPass.getDocument();

        List<TextEditorHighlightingPass> textPassesForDocument = textPasses.get(fileEditor);
        if (textPassesForDocument == null) {
          textPassesForDocument = new SmartList<TextEditorHighlightingPass>();
          textPasses.put(fileEditor, textPassesForDocument);
        }
        textPassesForDocument.add(textEditorHighlightingPass);

        List<FileEditor> editors = documentToEditors.get(document);
        if (editors == null) {
          editors = new SmartList<FileEditor>();
          documentToEditors.put(document, editors);
        }
        if (!editors.contains(fileEditor)) editors.add(fileEditor);
      }
    }

    List<ScheduledPass> freePasses = new ArrayList<ScheduledPass>(documentToEditors.size()*5);
    List<ScheduledPass> dependentPasses = new ArrayList<ScheduledPass>(documentToEditors.size()*10);
    final AtomicInteger threadsToStartCountdown = new AtomicInteger(0);
    for (List<FileEditor> fileEditors : documentToEditors.values()) {
      List<TextEditorHighlightingPass> passes = textPasses.get(fileEditors.get(0));
      threadsToStartCountdown.addAndGet(passes.size());

      // create one scheduled pass per unique id (possibly for multiple file editors. they all will be applied at the pass finish)
      ContainerUtil.quickSort(passes, new Comparator<TextEditorHighlightingPass>() {
        @Override
        public int compare(final TextEditorHighlightingPass o1, final TextEditorHighlightingPass o2) {
          return o1.getId() - o2.getId();
        }
      });
      int passId = -1;
      TextEditorHighlightingPass currentPass = null;
      for (int i = 0; i <= passes.size(); i++) {
        int newId = -1;
        if (i < passes.size()) {
          currentPass = passes.get(i);
          newId = currentPass.getId();
        }
        if (newId != passId) {
          createScheduledPass(fileEditors, currentPass, toBeSubmitted, passes, freePasses, dependentPasses, updateProgress, threadsToStartCountdown,
                              jobPriority);
          passId = newId;
        }
      }
    }

    log(updateProgress, null, "---------------------starting------------------------ " + threadsToStartCountdown.get(), freePasses);

    for (ScheduledPass dependentPass : dependentPasses) {
      mySubmittedPasses.put(dependentPass, Job.NULL_JOB);
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  @NotNull
  private ScheduledPass createScheduledPass(@NotNull List<FileEditor> fileEditors,
                                            @NotNull TextEditorHighlightingPass pass,
                                            @NotNull Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted,
                                            @NotNull List<TextEditorHighlightingPass> textEditorHighlightingPasses,
                                            @NotNull List<ScheduledPass> freePasses,
                                            @NotNull List<ScheduledPass> dependentPasses,
                                            @NotNull DaemonProgressIndicator updateProgress,
                                            @NotNull AtomicInteger threadsToStartCountdown,
                                            int jobPriority) {
    int passId = pass.getId();
    Document document = pass.getDocument();
    Pair<Document, Integer> key = Pair.create(document, passId);
    ScheduledPass scheduledPass = toBeSubmitted.get(key);
    if (scheduledPass != null) return scheduledPass;
    scheduledPass = new ScheduledPass(fileEditors, pass, updateProgress, threadsToStartCountdown, jobPriority);
    toBeSubmitted.put(key, scheduledPass);
    for (int predecessorId : pass.getCompletionPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditors, document, toBeSubmitted, textEditorHighlightingPasses, freePasses, dependentPasses,
                                                              updateProgress, threadsToStartCountdown, jobPriority, predecessorId);
      if (predecessor != null) {
        predecessor.mySuccessorsOnCompletion.add(scheduledPass);
        scheduledPass.myRunningPredecessorsCount.incrementAndGet();
      }
    }
    for (int predecessorId : pass.getStartingPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditors, document, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                              dependentPasses, updateProgress, threadsToStartCountdown, jobPriority, predecessorId);
      if (predecessor != null) {
        predecessor.mySuccessorsOnSubmit.add(scheduledPass);
        scheduledPass.myRunningPredecessorsCount.incrementAndGet();
      }
    }
    if (scheduledPass.myRunningPredecessorsCount.get() == 0 && !freePasses.contains(scheduledPass)) {
      freePasses.add(scheduledPass);
    }
    else if (!dependentPasses.contains(scheduledPass)) {
      dependentPasses.add(scheduledPass);
    }
    return scheduledPass;
  }

  private ScheduledPass findOrCreatePredecessorPass(@NotNull List<FileEditor> fileEditors,
                                                    Document document,
                                                    @NotNull Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted,
                                                    @NotNull List<TextEditorHighlightingPass> textEditorHighlightingPasses,
                                                    @NotNull List<ScheduledPass> freePasses,
                                                    @NotNull List<ScheduledPass> dependentPasses,
                                                    @NotNull DaemonProgressIndicator updateProgress,
                                                    @NotNull AtomicInteger myThreadsToStartCountdown,
                                                    final int jobPriority,
                                                    final int predecessorId) {
    Pair<Document, Integer> predKey = Pair.create(document, predecessorId);
    ScheduledPass predecessor = toBeSubmitted.get(predKey);
    if (predecessor == null) {
      TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
      predecessor = textEditorPass == null ? null : createScheduledPass(fileEditors, textEditorPass, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                                        dependentPasses, updateProgress, myThreadsToStartCountdown, jobPriority);
    }
    return predecessor;
  }

  private static TextEditorHighlightingPass findPassById(final int id, @NotNull List<TextEditorHighlightingPass> textEditorHighlightingPasses) {
    TextEditorHighlightingPass textEditorPass = null;
    for (TextEditorHighlightingPass found : textEditorHighlightingPasses) {
      if (found.getId() == id) {
        textEditorPass = found;
        break;
      }
    }
    return textEditorPass;
  }

  private void submit(@NotNull ScheduledPass pass) {
    if (!pass.myUpdateProgress.isCanceled()) {
      Job<Void> job = JobLauncher.getInstance().submitToJobThread(pass.myJobPriority, pass, new Consumer<Future>() {
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
    private final List<FileEditor> myFileEditors;
    private final TextEditorHighlightingPass myPass;
    private final AtomicInteger myThreadsToStartCountdown;
    private final int myJobPriority;
    private final AtomicInteger myRunningPredecessorsCount;
    private final Collection<ScheduledPass> mySuccessorsOnCompletion = new ArrayList<ScheduledPass>();
    private final Collection<ScheduledPass> mySuccessorsOnSubmit = new ArrayList<ScheduledPass>();
    private final DaemonProgressIndicator myUpdateProgress;

    private ScheduledPass(@NotNull List<FileEditor> fileEditors,
                          @NotNull TextEditorHighlightingPass pass,
                          @NotNull DaemonProgressIndicator progressIndicator,
                          @NotNull AtomicInteger threadsToStartCountdown,
                          int jobPriority) {
      myFileEditors = fileEditors;
      myPass = pass;
      myThreadsToStartCountdown = threadsToStartCountdown;
      myJobPriority = jobPriority;
      myRunningPredecessorsCount = new AtomicInteger(0);
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

                if (!myUpdateProgress.isCanceled()) {
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
        applyInformationToEditorsLater(myFileEditors, myPass, myUpdateProgress, myThreadsToStartCountdown);
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
  }

  private void applyInformationToEditorsLater(@NotNull final List<FileEditor> fileEditors,
                                              @NotNull final TextEditorHighlightingPass pass,
                                              @NotNull final DaemonProgressIndicator updateProgress,
                                              @NotNull final AtomicInteger threadsToStartCountdown) {
    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
    ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
      @Override
      public void run() {
        doApplyInformationToEditors(updateProgress, pass, fileEditors, threadsToStartCountdown, testMode);
      }
    }, ModalityState.stateForComponent(fileEditors.get(0).getComponent()));
  }

  private void doApplyInformationToEditors(@NotNull DaemonProgressIndicator updateProgress,
                                           @NotNull TextEditorHighlightingPass pass,
                                           @NotNull List<FileEditor> fileEditors,
                                           @NotNull AtomicInteger threadsToStartCountdown,
                                           boolean testMode) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isDisposed() || myProject.isDisposed()) {
      updateProgress.cancel();
    }
    if (updateProgress.isCanceled()) {
      log(updateProgress, pass, " is canceled during apply, sorry");
      return;
    }
    boolean applied = false;
    for (final FileEditor fileEditor : fileEditors) {
      LOG.assertTrue(fileEditor != null);
      try {
        if (testMode || fileEditor.getComponent().isDisplayable()) {
          if (!applied) {
            applied = true;
            log(updateProgress, pass, " Applied");
            pass.applyInformationToEditor();
          }
          afterApplyInformationToEditor(pass, fileEditor, updateProgress);

          if (pass.isRunIntentionPassAfter() && fileEditor instanceof TextEditor && !updateProgress.isCanceled()) {
            Editor editor = ((TextEditor)fileEditor).getEditor();
            ShowIntentionsPass ip = new ShowIntentionsPass(myProject, editor, -1);
            ip.setId(nextPassId.incrementAndGet());
            threadsToStartCountdown.incrementAndGet();
            submit(new ScheduledPass(fileEditors, ip, updateProgress, threadsToStartCountdown, Job.DEFAULT_PRIORITY));
          }
        }
      }
      catch (RuntimeException e) {
        log(updateProgress, pass, "Error " + e);
        throw e;
      }
    }
    if (threadsToStartCountdown.decrementAndGet() == 0) {
      log(updateProgress, pass, "Stopping ");
      updateProgress.stopIfRunning();
    }
    else {
      log(updateProgress, pass, "Finished but there are passes in the queue: "+threadsToStartCountdown.get());
    }
  }

  protected boolean isDisposed() {
    return isDisposed;
  }

  protected abstract void afterApplyInformationToEditor(TextEditorHighlightingPass pass, FileEditor fileEditor, ProgressIndicator updateProgress);

  @NotNull
  public List<TextEditorHighlightingPass> getAllSubmittedPasses() {
    List<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(mySubmittedPasses.size());
    for (ScheduledPass scheduledPass : mySubmittedPasses.keySet()) {
      if (!scheduledPass.myUpdateProgress.isCanceled()) {
        result.add(scheduledPass.myPass);
      }
    }
    ContainerUtil.quickSort(result, new Comparator<TextEditorHighlightingPass>() {
      @Override
      public int compare(TextEditorHighlightingPass o1, TextEditorHighlightingPass o2) {
        return o1.getId() - o2.getId();
      }
    });
    return result;
  }

  private static final ConcurrentHashMap<Thread, Integer> threads = new ConcurrentHashMap<Thread, Integer>();
  private static int getThreadNum() {
    return ConcurrencyUtil.cacheOrGet(threads, Thread.currentThread(), threads.size());
  }

  public static void log(ProgressIndicator progressIndicator, TextEditorHighlightingPass pass, @NonNls Object... info) {
    if (LOG.isDebugEnabled()) {
      CharSequence docText = pass == null ? "" : StringUtil.first(pass.getDocument().getCharsSequence(), 10, true);
      synchronized (PassExecutorService.class) {
        StringBuilder s = new StringBuilder();
        for (Object o : info) {
          s.append(o.toString()).append(" ");
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
  private static void saveException(Throwable e, DaemonProgressIndicator indicator) {
    indicator.putUserDataIfAbsent(THROWABLE_KEY, e);
  }
  public static Throwable getSavedException(DaemonProgressIndicator indicator) {
    return indicator.getUserData(THROWABLE_KEY);
  }

  // return true if terminated
  public boolean waitFor(int millis) throws Throwable {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
