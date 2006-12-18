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
import org.jetbrains.annotations.NonNls;

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
  private final ThreadPoolExecutor myExecutorService = new ThreadPoolExecutor(PROCESSORS, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),new ThreadFactory() {
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r,"Highlighting thread");
      t.setPriority(Thread.MIN_PRIORITY);
      return t;
    }
  });

  private final Map<ScheduledPass, Future<?>> mySubmittedPasses = new ConcurrentHashMap<ScheduledPass, Future<?>>();
  private final Project myProject;
  private final AtomicInteger myThreadsToExecuteCountdown = new AtomicInteger();
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  public PassExecutorService(DaemonCodeAnalyzerImpl daemonCodeAnalyzer, Project project) {
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
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
    synchronized(PassExecutorService.class) {LOG.debug("------------------------------------------");}
    cancelAll();
    final ProgressIndicator updateProgress = myDaemonCodeAnalyzer.getUpdateProgress();
    if (updateProgress instanceof DaemonProgressIndicator) {
      ((DaemonProgressIndicator)updateProgress).restart();
    }

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
            pass.collectInformation(updateProgress);
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
      createScheduledPass(fileEditor, pass, toBeSubmitted, textEditorHighlightingPasses, freePasses, updateProgress);
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private ScheduledPass createScheduledPass(final FileEditor fileEditor,
                                            final TextEditorHighlightingPass pass,
                                            final TIntObjectHashMap<ScheduledPass> toBeSubmitted,
                                            final TextEditorHighlightingPass[] textEditorHighlightingPasses,
                                            final List<ScheduledPass> freePasses, final ProgressIndicator updateProgress) {
    int passId = pass.getId();
    ScheduledPass scheduledPass = toBeSubmitted.get(passId);
    if (scheduledPass != null) return scheduledPass;
    int[] completionPredecessorIds = pass.getCompletionPredecessorIds();
    scheduledPass = new ScheduledPass(fileEditor, pass, updateProgress);
    toBeSubmitted.put(passId, scheduledPass);
    for (int predecessorId : completionPredecessorIds) {
      ScheduledPass predecessor = toBeSubmitted.get(predecessorId);
      if (predecessor == null) {
        TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
        predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted, textEditorHighlightingPasses,freePasses,
                                                                          updateProgress);
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
        predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted, textEditorHighlightingPasses,freePasses,
                                                                          updateProgress);
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
    if (!myDaemonCodeAnalyzer.getUpdateProgress().isCanceled()) {
      //LOG.debug(pass.myPass + " submitted at " + System.currentTimeMillis());
      Future<?> future = myExecutorService.submit(pass);
      mySubmittedPasses.put(pass, future);
    }
  }                                         

  public ExecutorService getDaemonExecutorService() {
    return myExecutorService;
  }

  private class ScheduledPass implements Runnable {
    private final FileEditor myFileEditor;
    private final TextEditorHighlightingPass myPass;
    private final AtomicInteger myRunningPredecessorsCount;
    private final Collection<ScheduledPass> mySuccessorsOnCompletion = new ArrayList<ScheduledPass>();
    private final Collection<ScheduledPass> mySuccessorsOnSubmit = new ArrayList<ScheduledPass>();
    private final ProgressIndicator myUpdateProgress;

    public ScheduledPass(final FileEditor fileEditor, TextEditorHighlightingPass pass, final ProgressIndicator progressIndicator) {
      myFileEditor = fileEditor;
      myPass = pass;
      myRunningPredecessorsCount = new AtomicInteger(0);
      myUpdateProgress = progressIndicator;
    }

    public void run() {
      info(myUpdateProgress, "Started " , myPass);
      Thread.currentThread().setName("Highlighting pass " + myPass);

      if (myUpdateProgress.isCanceled()) return;
      ((ProgressManagerImpl)ProgressManager.getInstance()).progressMe(myUpdateProgress);

      for (ScheduledPass successor : mySuccessorsOnSubmit) {
        int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
        if (predecessorsToRun == 0) {
          submit(successor);
        }
      }

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          try {
            if (myUpdateProgress.isCanceled()) { // IMPORTANT: to check here directly: to verify that nothing has changed before getting read lock!
              throw new ProcessCanceledException();
            }
            myPass.doCollectInformation(myUpdateProgress);
          }
          catch (ProcessCanceledException e) {
            info(myUpdateProgress, "Canceled ",myPass);
          }
        }
      });

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
      // check that it is not remnant from the previous attempt, canceled long ago
      if (myUpdateProgress == myDaemonCodeAnalyzer.getUpdateProgress()) {
        int toexec = myThreadsToExecuteCountdown.decrementAndGet();
        if (toexec == 0) {
          LOG.debug("Stopping");
          myUpdateProgress.stop();
        }
        else {
          //LOG.debug("Pass "+ myPass +" finished but there is the pass in the queue: "+mySubmittedPasses.keySet().iterator().next().myPass+"; toexec="+toexec);
        }
      }
      info(myUpdateProgress, "Finished " , myPass);
    }
  }

  private void applyInformationToEditor(final ScheduledPass pass) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    final boolean wasCanceled = pass.myUpdateProgress.isCanceled();
    final FileEditor editor = pass.myFileEditor;
    if (editor != null && !wasCanceled) {
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


  //private static final ConcurrentHashMap<Thread, Integer> threads = new ConcurrentHashMap<Thread, Integer>();
  //private static int getThreadNum() {
  //  int size = threads.size();
  //  Integer number = threads.putIfAbsent(Thread.currentThread(), size);
  //  if (number == null) number = size;
  //  return number;
  //}

  public static void info(ProgressIndicator progressIndicator, @NonNls Object... info) {
    //synchronized (PassExecutorService.class) {
    //  LOG.debug(StringUtil.repeatSymbol(' ', getThreadNum() * 4) + info
    //            + "; progress=" + (progressIndicator == null ? null : progressIndicator.hashCode())
    //            + "; canceled=" + (progressIndicator != null && progressIndicator.isCanceled())
    //  );
    //}
  }
}
