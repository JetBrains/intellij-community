package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import gnu.trove.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author cdr
 */
public class PassExecutorService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PassExecutorService");
  public static int PROCESSORS = Runtime.getRuntime().availableProcessors();
  private final ExecutorService myExecutorService = Executors.newFixedThreadPool(PROCESSORS, new ThreadFactory() {
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setPriority(Thread.MIN_PRIORITY);
      return t;
    }
  });
  private final Map<ScheduledPass, Future<?>> mySubmittedPasses = new ConcurrentHashMap<ScheduledPass, Future<?>>();
  private final ProgressIndicator myUpdateProgress;
  private final Project myProject;
  private final AtomicInteger myThreadsToExecuteCountdown = new AtomicInteger();
  private ExecutorService myDaemonExecutorService;

  public PassExecutorService(ProgressIndicator daemonProgress, Project project) {
    myUpdateProgress = daemonProgress;
    myProject = project;
  }
  public void dispose() {
    cancelAll();
    myExecutorService.shutdownNow();
  }
  public void cancelAll() {
    for (Future<?> submittedPass : mySubmittedPasses.values()) {
      submittedPass.cancel(false);
    }
    mySubmittedPasses.clear();
  }

  public void submitPasses(final FileEditor fileEditor, final HighlightingPass[] passes) {
    if (myUpdateProgress.isRunning()) {
      myUpdateProgress.stop();
    }
    myUpdateProgress.start();
    myThreadsToExecuteCountdown.set(passes.length);
    
    final TextEditorHighlightingPass[] textEditorHighlightingPasses;
    if (passes instanceof TextEditorHighlightingPass[]) {
      textEditorHighlightingPasses = (TextEditorHighlightingPass[])passes;
    }
    else {
      // run all passes in sequence
      textEditorHighlightingPasses = new TextEditorHighlightingPass[passes.length];
      for (int i = 0; i < passes.length; i++) {
        final HighlightingPass pass = passes[i];
        TextEditorHighlightingPass textEditorHighlightingPass = new TextEditorHighlightingPass(myProject, null) {
          public void doCollectInformation(ProgressIndicator progress) {
            pass.collectInformation(myUpdateProgress);
          }

          public void doApplyInformationToEditor() {
            pass.applyInformationToEditor();
          }
        };
        textEditorHighlightingPass.setId(i);
        if (i > 0) {
          textEditorHighlightingPass.setCompletionPredecessorIds(new int[]{i - 1});
        }
        textEditorHighlightingPasses[i] = textEditorHighlightingPass;
      }
    }

    TIntObjectHashMap<ScheduledPass> toBeSubmitted = new TIntObjectHashMap<ScheduledPass>();
    List<ScheduledPass> freePasses = new ArrayList<ScheduledPass>();
    for (final TextEditorHighlightingPass pass : textEditorHighlightingPasses) {
      createScheduledPass(fileEditor, pass, toBeSubmitted, textEditorHighlightingPasses, freePasses);
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private ScheduledPass createScheduledPass(final FileEditor fileEditor,
                                            final TextEditorHighlightingPass pass,
                                            final TIntObjectHashMap<ScheduledPass> toBeSubmitted,
                                            final TextEditorHighlightingPass[] textEditorHighlightingPasses,
                                            final List<ScheduledPass> freePasses) {
    int passId = pass.getId();
    ScheduledPass scheduledPass = toBeSubmitted.get(passId);
    if (scheduledPass != null) return scheduledPass;
    int[] completionPredecessorIds = pass.getCompletionPredecessorIds();
    scheduledPass = new ScheduledPass(fileEditor, pass);
    toBeSubmitted.put(passId, scheduledPass);
    for (int predecessorId : completionPredecessorIds) {
      ScheduledPass predecessor = toBeSubmitted.get(predecessorId);
      if (predecessor == null) {
        TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
        predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted, textEditorHighlightingPasses,freePasses);
      }
      if (predecessor != null) {
        predecessor.mySuccessorsOnCompletion.add(scheduledPass);
        scheduledPass.myRunningPredecessorsCount.incrementAndGet();
      }
    }
    int[] startingPredecessorIds = pass.getStartingPredecessorIds();
    for (int predecessorId : startingPredecessorIds) {
      ScheduledPass predecessor = toBeSubmitted.get(predecessorId);
      if (predecessor == null) {
        TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
        predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted, textEditorHighlightingPasses,freePasses);
      }
      if (predecessor != null) {
        predecessor.mySuccessorsOnSubmit.add(scheduledPass);
        scheduledPass.myRunningPredecessorsCount.incrementAndGet();
      }
    }
    if (completionPredecessorIds.length == 0 && startingPredecessorIds.length == 0 && !freePasses.contains(scheduledPass)) {
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
    if (!myUpdateProgress.isCanceled()) {
      LOG.debug(pass.myPass + " submitted at " + System.currentTimeMillis());
      Future<?> future = myExecutorService.submit(pass);
      mySubmittedPasses.put(pass, future);
    }
  }

  public ExecutorService getDaemonExecutorService() {
    return myDaemonExecutorService;
  }

  private class ScheduledPass implements Runnable {
    private final FileEditor myFileEditor;
    private final TextEditorHighlightingPass myPass;
    private AtomicInteger myRunningPredecessorsCount;
    private Collection<ScheduledPass> mySuccessorsOnCompletion = new ArrayList<ScheduledPass>();
    private Collection<ScheduledPass> mySuccessorsOnSubmit = new ArrayList<ScheduledPass>();

    public ScheduledPass(final FileEditor fileEditor, TextEditorHighlightingPass pass) {
      myFileEditor = fileEditor;
      myPass = pass;
      myRunningPredecessorsCount = new AtomicInteger(0);
    }

    public void run() {
      Thread.currentThread().setName("Highlighting pass " + myPass);
      ((ProgressManagerImpl)ProgressManager.getInstance()).progressMe(myUpdateProgress);
      if (myUpdateProgress.isCanceled()) return;
      LOG.debug(myPass + " started at " + System.currentTimeMillis());
      for (ScheduledPass successor : mySuccessorsOnSubmit) {
        int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
        if (predecessorsToRun == 0) {
          submit(successor);
        }
      }

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          try {
            if (myUpdateProgress
              .isCanceled()) { // IMPORTANT: to check here directly: to verify that nothing has changed before getting read lock!
              throw new ProcessCanceledException();
            }
            myPass.doCollectInformation(myUpdateProgress);
          }
          catch (ProcessCanceledException e) {
            LOG.debug(myPass + " canceled");
          }
        }
      });
      LOG.debug(myPass + " finished at " + System.currentTimeMillis());

      if (!myUpdateProgress.isCanceled()) {
        for (ScheduledPass successor : mySuccessorsOnCompletion) {
          int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
          if (predecessorsToRun == 0) {
            submit(successor);
          }
        }
        applyInformationToEditor(this);
      }

      mySubmittedPasses.remove(this);
      int toexec = myThreadsToExecuteCountdown.decrementAndGet();
      if (toexec == 0) {
        LOG.debug("Stopping");
        myUpdateProgress.cancel();
        myUpdateProgress.stop();
      }
      else {
        //LOG.debug("Pass "+ myPass +" finished but there is the pass in the queue: "+mySubmittedPasses.keySet().iterator().next().myPass+"; toexec="+toexec);
      }
    }
  }

  private void applyInformationToEditor(final ScheduledPass pass) {
    final boolean wasCanceled = myUpdateProgress.isCanceled();
    final boolean wasRunning = myUpdateProgress.isRunning();
    final FileEditor editor = pass.myFileEditor;
    if (editor != null && !wasCanceled && wasRunning) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;

          if (editor.getComponent().isDisplayable() || ApplicationManager.getApplication().isUnitTestMode()) {
            pass.myPass.applyInformationToEditor();
            if (editor instanceof TextEditor) {
              LOG.debug("Apply "+pass.myPass);
              ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).
                repaintErrorStripeRenderer(((TextEditor)editor).getEditor());
            }
          }
        }
      }, ModalityState.stateForComponent(editor.getComponent()));
    }
  }
}
