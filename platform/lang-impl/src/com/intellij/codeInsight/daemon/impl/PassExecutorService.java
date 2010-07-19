/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobImpl;
import com.intellij.concurrency.JobUtil;
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
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

  public void dispose() {
    cancelAll(true);
    isDisposed = true;
  }

  public void cancelAll(boolean waitForTermination) {
    for (Job<Void> submittedPass : mySubmittedPasses.values()) {
      submittedPass.cancel();
    }
    if (waitForTermination) {
      for (Job<Void> job : mySubmittedPasses.values()) {
        try {
          JobImpl ji = (JobImpl)job;
          if (!job.isDone()) ji.waitForTermination(ji.getTasks());
        }
        catch (Throwable throwable) {
          LOG.error(throwable);
        }
      }
    }
    mySubmittedPasses.clear();
  }

  public void submitPasses(Map<FileEditor, HighlightingPass[]> passesMap, DaemonProgressIndicator updateProgress, final int jobPriority) {
    if (isDisposed()) return;
    int id = 1;

    // (doc, passId) -> created pass
    Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted = new THashMap<Pair<Document, Integer>, ScheduledPass>(passesMap.size());
    // null keys are ok
    Map<Document, List<FileEditor>> documentToEditors = new HashMap<Document, List<FileEditor>>();
    Map<FileEditor, List<TextEditorHighlightingPass>> textPasses = new HashMap<FileEditor, List<TextEditorHighlightingPass>>(passesMap.size());
    final boolean dumb = DumbService.getInstance(myProject).isDumb();
    for (Map.Entry<FileEditor, HighlightingPass[]> entry : passesMap.entrySet()) {
      FileEditor fileEditor = entry.getKey();
      HighlightingPass[] passes = entry.getValue();
      Document document = null;
      if (fileEditor instanceof TextEditor) {
        Editor editor = ((TextEditor)fileEditor).getEditor();
        document = editor.getDocument();
      }

      for (int i = 0; i < passes.length; i++) {
        final HighlightingPass pass = passes[i];
        if (dumb && !DumbService.isDumbAware(pass)) {
          continue;
        }

        TextEditorHighlightingPass textEditorHighlightingPass;
        if (pass instanceof TextEditorHighlightingPass) {
          textEditorHighlightingPass = (TextEditorHighlightingPass)pass;
        }
        else {
          // run all passes in sequence
          textEditorHighlightingPass = new TextEditorHighlightingPass(myProject, document, true) {
            public void doCollectInformation(ProgressIndicator progress) {
              pass.collectInformation(progress);
            }

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

    List<ScheduledPass> freePasses = new ArrayList<ScheduledPass>(documentToEditors.size());
    final AtomicInteger threadsToStartCountdown = new AtomicInteger(0);
    for (List<FileEditor> fileEditors : documentToEditors.values()) {
      List<TextEditorHighlightingPass> passes = textPasses.get(fileEditors.get(0));
      threadsToStartCountdown.addAndGet(passes.size());

      // create one scheduled pass per unique id (possibly for multiple fileeditors. they all will be applied at the pass finish)
      ContainerUtil.quickSort(passes, new Comparator<TextEditorHighlightingPass>() {
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
          createScheduledPass(fileEditors, currentPass, toBeSubmitted, passes, freePasses, updateProgress, threadsToStartCountdown,
                              jobPriority);
          passId = newId;
        }
      }
    }

    log(updateProgress, null, "---------------------starting------------------------ " + threadsToStartCountdown.get());

    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private ScheduledPass createScheduledPass(@NotNull List<FileEditor> fileEditors,
                                            @NotNull TextEditorHighlightingPass pass,
                                            @NotNull Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted,
                                            @NotNull List<TextEditorHighlightingPass> textEditorHighlightingPasses,
                                            @NotNull List<ScheduledPass> freePasses,
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
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditors, document, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                              updateProgress, threadsToStartCountdown, jobPriority, predecessorId, passId);
      if (predecessor != null) {
        predecessor.mySuccessorsOnCompletion.add(scheduledPass);
        scheduledPass.myRunningPredecessorsCount.incrementAndGet();
      }
    }
    for (int predecessorId : pass.getStartingPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditors, document, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                              updateProgress, threadsToStartCountdown, jobPriority, predecessorId, passId);
      if (predecessor != null) {
        predecessor.mySuccessorsOnSubmit.add(scheduledPass);
        scheduledPass.myRunningPredecessorsCount.incrementAndGet();
      }
    }
    if (scheduledPass.myRunningPredecessorsCount.get() == 0 && !freePasses.contains(scheduledPass)) {
      freePasses.add(scheduledPass);
    }
    return scheduledPass;
  }

  private ScheduledPass findOrCreatePredecessorPass(final List<FileEditor> fileEditors,
                                                    final Document document,
                                                    final Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted,
                                                    final List<TextEditorHighlightingPass> textEditorHighlightingPasses,
                                                    final List<ScheduledPass> freePasses,
                                                    final DaemonProgressIndicator updateProgress,
                                                    final AtomicInteger myThreadsToStartCountdown,
                                                    final int jobPriority,
                                                    final int predecessorId, int passId) {
    Pair<Document, Integer> predkey = Pair.create(document, predecessorId);
    ScheduledPass predecessor = toBeSubmitted.get(predkey);
    if (predecessor == null) {
      TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
      if (textEditorPass == null && predecessorId == Pass.UPDATE_VISIBLE && passId != Pass.UPDATE_ALL && findPassById(Pass.UPDATE_ALL, textEditorHighlightingPasses) != null) {
        // when UPDATE_VISIBLE pass is not going to run, pretend that all dependent passes are depend on UPDATE_ALL pass instead
        return findOrCreatePredecessorPass(fileEditors, document, toBeSubmitted, textEditorHighlightingPasses, freePasses, updateProgress,
                                           myThreadsToStartCountdown, jobPriority, Pass.UPDATE_ALL, passId);
      }
      predecessor = textEditorPass == null ? null : createScheduledPass(fileEditors, textEditorPass, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                                        updateProgress, myThreadsToStartCountdown, jobPriority);
    }
    return predecessor;
  }

  private static TextEditorHighlightingPass findPassById(final int id, final List<TextEditorHighlightingPass> textEditorHighlightingPasses) {
    TextEditorHighlightingPass textEditorPass = null;
    for (TextEditorHighlightingPass found : textEditorHighlightingPasses) {
      if (found.getId() == id) {
        textEditorPass = found;
        break;
      }
    }
    return textEditorPass;
  }

  private void submit(ScheduledPass pass) {
    if (!pass.myUpdateProgress.isCanceled()) {
      Job<Void> job = JobUtil.submitToJobThread(pass, pass.myJobPriority);
      mySubmittedPasses.put(pass, job);
    }
  }

  public void renewVisiblePasses(final TextEditor textEditor, final HighlightingPass[] highlightingPasses,
                          final DaemonProgressIndicator visibleProgress) {
    for (ScheduledPass pass : mySubmittedPasses.keySet()) {
      if (pass.myUpdateProgress == visibleProgress) {
        return; //already scheduled. whenever something changes, it will be rescheduled anyway
      }
    }
    Map<FileEditor, HighlightingPass[]> passes = Collections.<FileEditor, HighlightingPass[]>singletonMap(textEditor, highlightingPasses);
    // higher priority
    submitPasses(passes, visibleProgress, Job.DEFAULT_PRIORITY-10);
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

    public void run() {
      if (myUpdateProgress.isCanceled()) return;

      log(myUpdateProgress, myPass, "Started. ");

      for (ScheduledPass successor : mySuccessorsOnSubmit) {
        int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
        if (predecessorsToRun == 0) {
          submit(successor);
        }
      }

      ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable(){
        public void run() {
          boolean success = ApplicationManagerEx.getApplicationEx().tryRunReadAction(new Runnable() {
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

                myUpdateProgress.cancel(); //in case when some smartasses throw PCE just for fun
              }
              catch (RuntimeException e) {
                LOG.error(e);
                throw e;
              }
              catch (Error e) {
                LOG.error(e);
                throw e;
              }
            }
          });

          if (!success) {
            myUpdateProgress.cancel();
          }
        }
      },myUpdateProgress);

      log(myUpdateProgress, myPass, "Finished. ");

      if (!myUpdateProgress.isCanceled()) {
        applyInformationToEditors(myFileEditors, myPass, myUpdateProgress, myThreadsToStartCountdown);
        for (ScheduledPass successor : mySuccessorsOnCompletion) {
          int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
          if (predecessorsToRun == 0) {
            submit(successor);
          }
        }
      }
    }
  }

  protected void applyInformationToEditors(final List<FileEditor> fileEditors, final TextEditorHighlightingPass pass, final DaemonProgressIndicator updateProgress,
                                           final AtomicInteger threadsToStartCountdown) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          doApplyInformationToEditors(updateProgress, pass, fileEditors, threadsToStartCountdown, true);
        }
      });
    }
    else {
      ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
        public void run() {
          doApplyInformationToEditors(updateProgress, pass, fileEditors, threadsToStartCountdown, false);
        }
      }, ModalityState.stateForComponent(fileEditors.get(0).getComponent()));
    }
  }

  private void doApplyInformationToEditors(DaemonProgressIndicator updateProgress,
                                           TextEditorHighlightingPass pass,
                                           List<FileEditor> fileEditors,
                                           AtomicInteger threadsToStartCountdown,
                                           boolean testMode) {
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

  public List<TextEditorHighlightingPass> getAllSubmittedPasses() {
    ArrayList<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(mySubmittedPasses.size());
    for (ScheduledPass scheduledPass : mySubmittedPasses.keySet()) {
      if (!scheduledPass.myUpdateProgress.isCanceled()) {
        result.add(scheduledPass.myPass);
      }
    }
    return result;
  }

  private static final ConcurrentHashMap<Thread, Integer> threads = new ConcurrentHashMap<Thread, Integer>();
  private static int getThreadNum() {
    return ConcurrencyUtil.cacheOrGet(threads, Thread.currentThread(), threads.size());
  }

  public static void log(ProgressIndicator progressIndicator, TextEditorHighlightingPass pass, @NonNls Object... info) {
    if (LOG.isDebugEnabled()) {
      synchronized (PassExecutorService.class) {
        StringBuilder s = new StringBuilder();
        for (Object o : info) {
          s.append(o.toString());
        }
        LOG.debug(StringUtil.repeatSymbol(' ', getThreadNum() * 4)
                  + " "+pass+" "
                  + s
                  + "; progress=" + (progressIndicator == null ? null : progressIndicator.hashCode())
                  + " " + (progressIndicator == null ? "?" : progressIndicator.isCanceled() ? "X" : "V")
                  + " : '"+(pass == null ? "" : StringUtil.first(pass.getDocument().getText(), 10, true)) + "'"
        );
      }
    }
  }
}
