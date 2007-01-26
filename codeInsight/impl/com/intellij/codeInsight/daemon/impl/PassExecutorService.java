package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
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
public abstract class PassExecutorService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PassExecutorService");
  public static int PROCESSORS = /*10;//*/Runtime.getRuntime().availableProcessors();
  private final ThreadPoolExecutor myExecutorService = new ThreadPoolExecutor(PROCESSORS, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),new ThreadFactory() {
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, "Highlighting thread");
      t.setPriority(Thread.MIN_PRIORITY);
      return t;
    }
  }){
    public Future<?> submit(final Runnable runnable) {
      MyFutureTask task = new MyFutureTask(runnable);
      execute(task);
      return task;
    }
  };

  private final Map<ScheduledPass, Future<?>> mySubmittedPasses = new ConcurrentHashMap<ScheduledPass, Future<?>>();
  private final Project myProject;

  public PassExecutorService(Project project) {
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

  public void submitPasses(final Map<FileEditor, HighlightingPass[]> passesMap, final DaemonProgressIndicator updateProgress) {
    final AtomicInteger threadsToStartCountdown = new AtomicInteger(0);

    List<ScheduledPass> freePasses = new ArrayList<ScheduledPass>();
    TIntObjectHashMap<ScheduledPass> toBeSubmitted = new TIntObjectHashMap<ScheduledPass>();
    for (FileEditor fileEditor : passesMap.keySet()) {
      HighlightingPass[] passes = passesMap.get(fileEditor);
      TextEditorHighlightingPass[] passesToAdd;
      if (passes instanceof TextEditorHighlightingPass[]) {
        passesToAdd = (TextEditorHighlightingPass[])passes;
      }
      else {
        // run all passes in sequence
        passesToAdd = new TextEditorHighlightingPass[passes.length];
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
          passesToAdd[i] = textEditorHighlightingPass;
        }
      }
      threadsToStartCountdown.addAndGet(passesToAdd.length);
      for (final TextEditorHighlightingPass pass : passesToAdd) {
        createScheduledPass(fileEditor, pass, toBeSubmitted, passesToAdd, freePasses, updateProgress, threadsToStartCountdown);
      }
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private ScheduledPass createScheduledPass(final FileEditor fileEditor,
                                            final TextEditorHighlightingPass pass,
                                            final TIntObjectHashMap<ScheduledPass> toBeSubmitted,
                                            final TextEditorHighlightingPass[] textEditorHighlightingPasses,
                                            final List<ScheduledPass> freePasses,
                                            final DaemonProgressIndicator updateProgress,
                                            final AtomicInteger myThreadsToStartCountdown) {
    int passId = pass.getId();
    ScheduledPass scheduledPass = toBeSubmitted.get(passId);
    if (scheduledPass != null) return scheduledPass;
    int[] completionPredecessorIds = pass.getCompletionPredecessorIds();
    scheduledPass = new ScheduledPass(fileEditor, pass, updateProgress, myThreadsToStartCountdown);
    toBeSubmitted.put(passId, scheduledPass);
    for (int predecessorId : completionPredecessorIds) {
      ScheduledPass predecessor = toBeSubmitted.get(predecessorId);
      if (predecessor == null) {
        TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
        predecessor = textEditorPass == null ? null : createScheduledPass(fileEditor, textEditorPass, toBeSubmitted, textEditorHighlightingPasses,freePasses,
                                                                          updateProgress, myThreadsToStartCountdown);
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
                                                                          updateProgress, myThreadsToStartCountdown);
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

  private static class MyFutureTask extends FutureTask {
    public MyFutureTask(Runnable runnable) {
      super(runnable, null);
    }

    protected void done() {
      try {
        //allow exceptions to manifest themselves
        get();
      }
      catch (CancellationException e) {
        //ok
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (ExecutionException e) {
        LOG.error(e.getCause());
      }
    }
  }
  private void submit(ScheduledPass pass) {
    if (!pass.myUpdateProgress.isCanceled()) {
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
    private final AtomicInteger myThreadsToStartCountdown;
    private final AtomicInteger myRunningPredecessorsCount;
    private final Collection<ScheduledPass> mySuccessorsOnCompletion = new ArrayList<ScheduledPass>();
    private final Collection<ScheduledPass> mySuccessorsOnSubmit = new ArrayList<ScheduledPass>();
    private final DaemonProgressIndicator myUpdateProgress;

    public ScheduledPass(final FileEditor fileEditor,
                         TextEditorHighlightingPass pass,
                         final DaemonProgressIndicator progressIndicator,
                         final AtomicInteger myThreadsToStartCountdown) {
      myFileEditor = fileEditor;
      myPass = pass;
      this.myThreadsToStartCountdown = myThreadsToStartCountdown;
      myRunningPredecessorsCount = new AtomicInteger(0);
      myUpdateProgress = progressIndicator;
    }

    public void run() {
      log(myUpdateProgress, "Started " , myPass);
      Thread.currentThread().setName("Highlighting pass " + myPass);

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
                if (myUpdateProgress.isCanceled()) { // IMPORTANT: to check here directly: to verify that nothing has changed before getting read lock!
                  throw new ProcessCanceledException();
                }
                myPass.doCollectInformation(myUpdateProgress);
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
