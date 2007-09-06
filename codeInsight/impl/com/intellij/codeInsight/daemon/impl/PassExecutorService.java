package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author cdr
 */
public abstract class PassExecutorService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PassExecutorService");

  private final Map<ScheduledPass, Job<Void>> mySubmittedPasses = new ConcurrentHashMap<ScheduledPass, Job<Void>>();
  private final Project myProject;
  private boolean isDisposed;

  public PassExecutorService(Project project) {
    myProject = project;
  }

  public void dispose() {
    cancelAll();
    isDisposed = true;
  }

  public void cancelAll() {
    for (Job<Void> submittedPass : mySubmittedPasses.values()) {
      submittedPass.cancel();
    }
    mySubmittedPasses.clear();
  }

  public void submitPasses(Map<FileEditor, HighlightingPass[]> passesMap, DaemonProgressIndicator updateProgress, final int jobPriority) {
    log(updateProgress, null, "---------------------------------------------");
    int id = 1;

    // (doc, passId) -> created pass
    Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted = new THashMap<Pair<Document, Integer>, ScheduledPass>(passesMap.size());
    // null keys are ok
    Map<Document, List<FileEditor>> documentToEditors = new HashMap<Document, List<FileEditor>>();
    Map<FileEditor, List<TextEditorHighlightingPass>> textPasses = new HashMap<FileEditor, List<TextEditorHighlightingPass>>(passesMap.size());
    for (FileEditor fileEditor : passesMap.keySet()) {
      Document document = null;
      if (fileEditor instanceof TextEditor) {
        Editor editor = ((TextEditor)fileEditor).getEditor();
        document = editor.getDocument();
      }
      HighlightingPass[] passes = passesMap.get(fileEditor);

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
        document = textEditorHighlightingPass.getDocument();

        List<TextEditorHighlightingPass> textPassesForDocument = textPasses.get(fileEditor);
        if (textPassesForDocument == null) {
          textPassesForDocument = new SmartList<TextEditorHighlightingPass>();
          textPasses.put(fileEditor, textPassesForDocument);
        }
        textPassesForDocument.add(textEditorHighlightingPass);

        List<FileEditor> editors = documentToEditors.get(document);
        if (editors == null) {
          editors= new SmartList<FileEditor>();
          documentToEditors.put(document, editors);
        }
        if (!editors.contains(fileEditor)) editors.add(fileEditor);
      }
    }

    List<ScheduledPass> freePasses = new ArrayList<ScheduledPass>(documentToEditors.size());
    final AtomicInteger threadsToStartCountdown = new AtomicInteger(0);
    for (Document document : documentToEditors.keySet()) {
      List<FileEditor> fileEditors = documentToEditors.get(document);
      List<TextEditorHighlightingPass> passes = textPasses.get(fileEditors.get(0));
      threadsToStartCountdown.addAndGet(passes.size());

      // create one scheduled pass per unique id (possibly for multiple fileeditors. they all will be applied at the pass finish)
      Collections.sort(passes, new Comparator<TextEditorHighlightingPass>(){
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
          createScheduledPass(fileEditors, currentPass, toBeSubmitted, passes, freePasses, updateProgress, threadsToStartCountdown, jobPriority);
          passId = newId;
        }
      }
    }
    for (ScheduledPass freePass : freePasses) {
      submit(freePass);
    }
  }

  private ScheduledPass createScheduledPass(final List<FileEditor> fileEditors,
                                            final TextEditorHighlightingPass pass,
                                            final Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted,
                                            final List<TextEditorHighlightingPass> textEditorHighlightingPasses,
                                            final List<ScheduledPass> freePasses,
                                            final DaemonProgressIndicator updateProgress,
                                            final AtomicInteger threadsToStartCountdown,
                                            final int jobPriority) {
    int passId = pass.getId();
    Document document = pass.getDocument();
    Pair<Document, Integer> key = Pair.create(document, passId);
    ScheduledPass scheduledPass = toBeSubmitted.get(key);
    if (scheduledPass != null) return scheduledPass;
    scheduledPass = new ScheduledPass(fileEditors, pass, updateProgress, threadsToStartCountdown, jobPriority);
    toBeSubmitted.put(key, scheduledPass);
    for (int predecessorId : pass.getCompletionPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditors, document, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                              updateProgress, threadsToStartCountdown, jobPriority, predecessorId);
      if (predecessor != null) {
        predecessor.mySuccessorsOnCompletion.add(scheduledPass);
        scheduledPass.myRunningPredecessorsCount.incrementAndGet();
      }
    }
    for (int predecessorId : pass.getStartingPredecessorIds()) {
      ScheduledPass predecessor = findOrCreatePredecessorPass(fileEditors, document, toBeSubmitted, textEditorHighlightingPasses, freePasses,
                                                              updateProgress, threadsToStartCountdown, jobPriority, predecessorId);
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

  private ScheduledPass findOrCreatePredecessorPass(final List<FileEditor> fileEditors, final Document document, final Map<Pair<Document, Integer>, ScheduledPass> toBeSubmitted,
                                                    final List<TextEditorHighlightingPass> textEditorHighlightingPasses,
                                                    final List<ScheduledPass> freePasses,
                                                    final DaemonProgressIndicator updateProgress,
                                                    final AtomicInteger myThreadsToStartCountdown,
                                                    final int jobPriority,
                                                    final int predecessorId) {
    Pair<Document, Integer> predkey = Pair.create(document, predecessorId);
    ScheduledPass predecessor = toBeSubmitted.get(predkey);
    if (predecessor == null) {
      TextEditorHighlightingPass textEditorPass = findPassById(predecessorId, textEditorHighlightingPasses);
      predecessor = textEditorPass == null ? null : createScheduledPass(fileEditors, textEditorPass, toBeSubmitted, textEditorHighlightingPasses,freePasses,
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

    public ScheduledPass(List<FileEditor> fileEditors,
                         TextEditorHighlightingPass pass,
                         DaemonProgressIndicator progressIndicator,
                         AtomicInteger threadsToStartCountdown,
                         int jobPriority) {
      myFileEditors = fileEditors;
      myPass = pass;
      myThreadsToStartCountdown = threadsToStartCountdown;
      myJobPriority = jobPriority;
      myRunningPredecessorsCount = new AtomicInteger(0);
      myUpdateProgress = progressIndicator;
    }

    public void run() {
      log(myUpdateProgress, myPass, "Started ");

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
                if (!myUpdateProgress.isCanceled()) {
                  myPass.collectInformation(myUpdateProgress);
                }
              }
              catch (ProcessCanceledException e) {
                log(myUpdateProgress, myPass, "Canceled ");
              }
              catch(RuntimeException e) {
                LOG.error(e);
                throw e;
              }
              catch(Error e) {
                LOG.error(e);
                throw e;
              }
            }
          });
        }
      },myUpdateProgress);

      boolean hasMoreWorkTodo = myThreadsToStartCountdown.decrementAndGet() != 0;
      if (!myUpdateProgress.isCanceled()) {
        applyInformationToEditors(hasMoreWorkTodo);
        for (ScheduledPass successor : mySuccessorsOnCompletion) {
          int predecessorsToRun = successor.myRunningPredecessorsCount.decrementAndGet();
          if (predecessorsToRun == 0) {
            submit(successor);
          }
        }
        if (!hasMoreWorkTodo) {
          log(myUpdateProgress, myPass, "Stopping ");
          myUpdateProgress.stopIfRunning();
        }
        else {
          log(myUpdateProgress, myPass, "Pass finished but there are passes in the queue");
        }
      }
      log(myUpdateProgress, myPass, "Finished ");
    }

    private void applyInformationToEditors(final boolean hasMoreWorkTodo) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      if (myUpdateProgress.isCanceled()) return;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (isDisposed() || myProject.isDisposed()) return;
          boolean applied = false;
          for (final FileEditor fileEditor : myFileEditors) {
            LOG.assertTrue(fileEditor != null);
            if (fileEditor.getComponent().isDisplayable()) {
              if (!applied) {
                applied = true;
                myPass.applyInformationToEditor();
                Document document = myPass.getDocument();
                if (!hasMoreWorkTodo && document != null) {
                  reportToWolf(document, myProject);
                }
              }
              afterApplyInformationToEditor(myPass, fileEditor, myUpdateProgress);
            }
          }
        }
      }, ModalityState.stateForComponent(myFileEditors.get(0).getComponent()));
    }
  }

  public static void reportToWolf(@NotNull final Document document, @NotNull Project project) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) return;
    HighlightInfo[] errors = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.ERROR, project);
    GeneralHighlightingPass.reportErrorsToWolf(errors, psiFile);
  }

  protected boolean isDisposed() {
    return isDisposed;
  }

  protected abstract void afterApplyInformationToEditor(TextEditorHighlightingPass pass, FileEditor fileEditor, ProgressIndicator updateProgress);

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

  public static void log(ProgressIndicator progressIndicator, TextEditorHighlightingPass pass, @NonNls Object... info) {
    if (LOG.isDebugEnabled()) {
      synchronized (PassExecutorService.class) {
        StringBuffer s = new StringBuffer();
        for (Object o : info) {
          s.append(o.toString());
        }
        LOG.debug(StringUtil.repeatSymbol(' ', getThreadNum() * 4)
                  + " "+pass+" "
                  + s
                  + "; progress=" + (progressIndicator == null ? null : progressIndicator.hashCode())
                  + " : '"+(pass == null ? "" : StringUtil.first(pass.getDocument().getText(), 30, true))
        );
      }
    }
  }
}
