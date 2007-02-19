package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author cdr
 */
public abstract class PassExecutorService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PassExecutorService");

  private final Map<ScheduledPass, Job<Void>> mySubmittedPasses = new ConcurrentHashMap<ScheduledPass, Job<Void>>();
  private final Project myProject;

  public PassExecutorService(Project project) {
    myProject = project;
  }

  public void dispose() {
    cancelAll();
  }

  public void cancelAll() {
    for (Job<Void> submittedPass : mySubmittedPasses.values()) {
      submittedPass.cancel();
    }
    mySubmittedPasses.clear();
  }

  public void submitPasses(Map<FileEditor, HighlightingPass[]> passesMap, DaemonProgressIndicator updateProgress, final int jobPriority) {
    final AtomicInteger threadsToStartCountdown = new AtomicInteger(0);
    int id = 1;

    List<ScheduledPass> freePasses = new ArrayList<ScheduledPass>();
    // (editor, passId) -> created pass
    Map<Pair<FileEditor, Integer>, ScheduledPass> toBeSubmitted = new THashMap<Pair<FileEditor, Integer>, ScheduledPass>();
    for (FileEditor fileEditor : passesMap.keySet()) {
      Document document = null;
      if (fileEditor instanceof TextEditor) {
        Editor editor = ((TextEditor)fileEditor).getEditor();
        document = editor.getDocument();
      }
      HighlightingPass[] passes = passesMap.get(fileEditor);
      TextEditorHighlightingPass[] passesToAdd = new TextEditorHighlightingPass[passes.length];
      for (int i = 0; i < passes.length; i++) {
        final HighlightingPass pass = passes[i];
        TextEditorHighlightingPass textEditorHighlightingPass;
        if (pass instanceof TextEditorHighlightingPass) {
          textEditorHighlightingPass = (TextEditorHighlightingPass)pass;
        }
        else {
          // run all passes in sequence
          textEditorHighlightingPass = new TextEditorHighlightingPass(myProject, document) {
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
        passesToAdd[i] = textEditorHighlightingPass;
      }
      threadsToStartCountdown.addAndGet(passesToAdd.length);
      for (final TextEditorHighlightingPass pass : passesToAdd) {
        createScheduledPass(fileEditor, pass, toBeSubmitted, passesToAdd, freePasses, updateProgress, threadsToStartCountdown, jobPriority);
      }
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private ScheduledPass createScheduledPass(final FileEditor fileEditor,
                                            final TextEditorHighlightingPass pass,
                                            final Map<Pair<FileEditor, Integer>, ScheduledPass> toBeSubmitted,
                                            final TextEditorHighlightingPass[] textEditorHighlightingPasses,
                                            final List<ScheduledPass> freePasses,
                                            final DaemonProgressIndicator updateProgress,
                                            final AtomicInteger myThreadsToStartCountdown,
                                            final int jobPriority) {
    int passId = pass.getId();
    Pair<FileEditor, Integer> key = Pair.create(fileEditor, passId);
    ScheduledPass scheduledPass = toBeSubmitted.get(key);
    if (scheduledPass != null) return scheduledPass;
    scheduledPass = new ScheduledPass(fileEditor, pass, updateProgress, myThreadsToStartCountdown, jobPriority);
    toBeSubmitted.put(key, scheduledPass);
    for (int predecessorId : pass.getCompletionPredecessorIds()) {
      Pair<FileEditor, Integer> predkey = Pair.create(fileEditor, predecessorId);
      ScheduledPass predecessor = toBeSubmitted.get(predkey);
      if (predecessor == null) {
        TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
        predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted, textEditorHighlightingPasses,freePasses,
                                                                          updateProgress, myThreadsToStartCountdown, jobPriority);
      }
      if (predecessor != null) {
        predecessor.mySuccessorsOnCompletion.add(scheduledPass);
        scheduledPass.myRunningPredecessorsCount.incrementAndGet();
      }
    }
    for (int predecessorId : pass.getStartingPredecessorIds()) {
      Pair<FileEditor, Integer> predkey = Pair.create(fileEditor, predecessorId);
      ScheduledPass predecessor = toBeSubmitted.get(predkey);
      if (predecessor == null) {
        TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
        predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted, textEditorHighlightingPasses,freePasses,
                                                                          updateProgress, myThreadsToStartCountdown, jobPriority);
      }
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

  private static TextEditorHighlightingPass findPassById(final int predecessorId, final TextEditorHighlightingPass[] textEditorHighlightingPasses) {
    TextEditorHighlightingPass textEditorPass = null;
    for (TextEditorHighlightingPass found : textEditorHighlightingPasses) {
      if (found.getId() == predecessorId) {
        textEditorPass = found;
        break;
      }
    }
    return textEditorPass;
  }

  private void submit(ScheduledPass pass) {
    if (!pass.myUpdateProgress.isCanceled()) {
      Job<Void> job = JobScheduler.getInstance().createJob(pass.myPass.toString(), pass.myJobPriority);
      job.addTask(pass);
      job.schedule();
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
    THashMap<FileEditor, HighlightingPass[]> passes = new THashMap<FileEditor, HighlightingPass[]>() {
      {
        put(textEditor, highlightingPasses);
      }
    };
    // higher priority
    submitPasses(passes, visibleProgress, Job.DEFAULT_PRIORITY-10);
  }

  private class ScheduledPass implements Runnable {
    private final FileEditor myFileEditor;
    private final TextEditorHighlightingPass myPass;
    private final AtomicInteger myThreadsToStartCountdown;
    private final int myJobPriority;
    private final AtomicInteger myRunningPredecessorsCount;
    private final Collection<ScheduledPass> mySuccessorsOnCompletion = new ArrayList<ScheduledPass>();
    private final Collection<ScheduledPass> mySuccessorsOnSubmit = new ArrayList<ScheduledPass>();
    private final DaemonProgressIndicator myUpdateProgress;

    public ScheduledPass(final FileEditor fileEditor,
                         TextEditorHighlightingPass pass,
                         final DaemonProgressIndicator progressIndicator,
                         final AtomicInteger myThreadsToStartCountdown, final int jobPriority) {
      myFileEditor = fileEditor;
      myPass = pass;
      this.myThreadsToStartCountdown = myThreadsToStartCountdown;
      myJobPriority = jobPriority;
      myRunningPredecessorsCount = new AtomicInteger(0);
      myUpdateProgress = progressIndicator;
    }

    public void run() {
      log(myUpdateProgress, "Started " , myPass);

      if (myUpdateProgress.isCanceled()) return;

      for (ScheduledPass successor : mySuccessorsOnSubmit) {
        int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
        if (predecessorsToRun == 0) {
          submit(successor);
        }
      }

      ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable(){
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                if (myUpdateProgress.isCanceled()) {
                  throw new ProcessCanceledException();
                }
                myPass.collectInformation(myUpdateProgress);
              }
              catch (ProcessCanceledException e) {
                log(myUpdateProgress, "Canceled ",myPass);
              }
            }
          });
        }
      },myUpdateProgress);

      if (!myUpdateProgress.isCanceled()) {
        for (ScheduledPass successor : mySuccessorsOnCompletion) {
          int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
          if (predecessorsToRun == 0) {
            submit(successor);
          }
        }
        applyInformationToEditor(this);
      }

      //mySubmittedPasses.remove(this);

      // check that it is not remnant from the previous attempt, canceled long ago
      if (!myUpdateProgress.isCanceled()) {
        int toexec = myThreadsToStartCountdown.decrementAndGet();
        LOG.assertTrue(toexec >= 0);
        if (toexec == 0) {
          log(myUpdateProgress, "Stopping ", myPass);
          myUpdateProgress.stopIfRunning();
        }
        else {
          log(myUpdateProgress, "Pass ", myPass ," finished but there are",toexec," passes in the queue");
        }
      }
      log(myUpdateProgress, "Finished " , myPass);
    }
  }

  private void applyInformationToEditor(final ScheduledPass pass) {
    applyInformationToEditor(pass.myPass, pass.myFileEditor, pass.myUpdateProgress);
  }

  protected abstract void applyInformationToEditor(final TextEditorHighlightingPass pass, final FileEditor fileEditor,
                                        final ProgressIndicator updateProgress);

  public List<TextEditorHighlightingPass> getAllSubmittedPasses() {
    ArrayList<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(mySubmittedPasses.size());
    for (ScheduledPass scheduledPass : mySubmittedPasses.keySet()) {
      result.add(scheduledPass.myPass);
    }
    return result;
  }

  private static final ConcurrentHashMap<Thread, Integer> threads = new ConcurrentHashMap<Thread, Integer>();
  private static int getThreadNum() {
    return ConcurrencyUtil.cacheOrGet(threads, Thread.currentThread(), threads.size());
  }

  public static void log(ProgressIndicator progressIndicator, @NonNls Object... info) {
    if (LOG.isDebugEnabled()) {
      synchronized (PassExecutorService.class) {
        StringBuffer s = new StringBuffer();
        for (Object o : info) {
          s.append(o.toString());
        }
        LOG.debug(StringUtil.repeatSymbol(' ', getThreadNum() * 4) + s
                  + "; progress=" + (progressIndicator == null ? null : progressIndicator.hashCode())
                  + "; canceled=" + (progressIndicator != null && progressIndicator.isCanceled())
                  + "; running=" + (progressIndicator != null && progressIndicator.isRunning())
        );
      }
    }
  }
}
