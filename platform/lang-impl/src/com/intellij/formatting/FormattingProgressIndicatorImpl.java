/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Formatting progressable task.  
 * 
 * @author Denis Zhdanov
 * @since 2/10/11 3:00 PM
 */
public class FormattingProgressIndicatorImpl extends Task.Modal implements FormattingProgressIndicator {

  /**
   * Holds flag that indicates whether formatting was cancelled by end-user or not.
   */
  public static final ThreadLocal<Boolean> FORMATTING_CANCELLED_FLAG = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return false;
    }
  };
  
  private static final Logger LOG = Logger.getInstance("#" + FormattingProgressIndicatorImpl.class.getName());

  /**
   * We want to perform formatting by big chunks at EDT. However, there is a possible case that particular formatting iteration
   * is executed in short amount of time. Hence, we may want to execute more than one formatting action during single EDT iteration.
   * Current collection contains mappings between min amount of time allowed for particular state iteration processing from EDT.
   */
  private static final TObjectIntHashMap<FormattingStateId> ITERATION_MIN_TIMES_MILLIS = new TObjectIntHashMap<FormattingStateId>();
  static {
    ITERATION_MIN_TIMES_MILLIS.put(FormattingStateId.WRAPPING_BLOCKS, 500);
    ITERATION_MIN_TIMES_MILLIS.put(FormattingStateId.PROCESSING_BLOCKS, 500);
    ITERATION_MIN_TIMES_MILLIS.put(FormattingStateId.APPLYING_CHANGES, 1000);
    assert ITERATION_MIN_TIMES_MILLIS.size() == FormattingStateId.values().length;
  }
  
  /**
   * Holds max allowed progress bar value (defined at ProgressWindow.MyDialog.initDialog()).
   */
  private static final double MAX_PROGRESS_VALUE = 1;
  private static final double TOTAL_WEIGHT;
  static {
    double weight = 0;
    for (FormattingStateId state : FormattingStateId.values()) {
      weight += state.getProgressWeight();
    }
    TOTAL_WEIGHT = weight;
  }
  
  private final Map<EventType, Collection<Runnable>> myCallbacks = new HashMap<EventType, Collection<Runnable>>();

  private final WeakReference<VirtualFile> myFile;
  private final WeakReference<Document>    myDocument;
  private final int                        myFileTextLength;

  @NotNull
  private          FormattingStateId myLastState                       = FormattingStateId.WRAPPING_BLOCKS;
  private          long              myDocumentModificationStampBefore = -1;
  private volatile boolean           myRunning                         = true;

  private ProgressIndicator myIndicator;
  private SequentialTask    myTask;
  private int               myBlocksToModifyNumber;
  private int               myModifiedBlocksNumber;
  
  public FormattingProgressIndicatorImpl(@Nullable Project project, @NotNull PsiFile file, @NotNull Document document) {
    super(project, getTitle(file), true);
    myFile = new WeakReference<VirtualFile>(file.getVirtualFile());
    myDocument = new WeakReference<Document>(document);
    myFileTextLength = file.getTextLength();
    addCallback(EventType.CANCEL, new MyCancelCallback());
  }
  
  @NotNull
  private static String getTitle(@NotNull PsiFile file) {
    VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
    if (virtualFile == null) {
      return CodeInsightBundle.message("reformat.progress.common.text");
    }
    else {
      return CodeInsightBundle.message("reformat.progress.file.with.known.name.text", virtualFile.getName());
    }
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      doRun(indicator);
    }
    catch (Exception e) {
      LOG.info("Unexpected exception occurred during reformatting file " + myFile, e);
    }
    finally {
      if (myIndicator != null) {
        myIndicator.stop();
      }
    }
  }

  public void doRun(@NotNull ProgressIndicator indicator) throws InvocationTargetException, InterruptedException {
    final SequentialTask task = myTask;
    if (task == null) {
      return;
    }

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        Document document = myDocument.get();
        if (document != null) {
          myDocumentModificationStampBefore = document.getModificationStamp();
        }
        task.prepare();
      }
    });

    // We need to sync background thread and EDT here in order to avoid situation when event queue is full of processing requests.
    myIndicator = indicator;
    while (myRunning && !task.isDone()) {
      if (indicator.isCanceled()) {
        task.stop();
        break;
      }
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          long start = System.currentTimeMillis();
          try {
            while (!task.isDone() && System.currentTimeMillis() - start < ITERATION_MIN_TIMES_MILLIS.get(myLastState)) {
              task.iteration();
            }
          }
          catch (RuntimeException e) {
            task.stop();
            throw e;
          }
        }
      });
    }
  }

  @Override
  public boolean addCallback(@NotNull EventType eventType, @NotNull Runnable callback) {
    return getCallbacks(eventType).add(callback);
  }

  @Override
  public void onSuccess() {
    super.onSuccess();
    for (Runnable callback : getCallbacks(EventType.SUCCESS)) {
      callback.run();
    }
  }

  @Override
  public void onCancel() {
    super.onCancel();
    for (Runnable callback : getCallbacks(EventType.CANCEL)) {
      callback.run();
    }
  }

  private Collection<Runnable> getCallbacks(@NotNull EventType eventType) {
    Collection<Runnable> result = myCallbacks.get(eventType);
    if (result == null) {
      myCallbacks.put(eventType, result = new HashSet<Runnable>());
    }
    return result;
  }

  @Override
  public void afterWrappingBlock(@NotNull LeafBlockWrapper wrapped) {
    update(FormattingStateId.WRAPPING_BLOCKS, MAX_PROGRESS_VALUE * wrapped.getEndOffset() / myFileTextLength);
  }
  
  @Override
  public void afterProcessingBlock(@NotNull LeafBlockWrapper block) {
    update(FormattingStateId.PROCESSING_BLOCKS, MAX_PROGRESS_VALUE * block.getEndOffset() / myFileTextLength);
  }
  
  @Override
  public void beforeApplyingFormatChanges(@NotNull Collection<LeafBlockWrapper> modifiedBlocks) {
    myBlocksToModifyNumber = modifiedBlocks.size();
    updateTextIfNecessary(FormattingStateId.APPLYING_CHANGES);
    setCancelText(IdeBundle.message("action.stop"));
  }
  
  @Override
  public void afterApplyingChange(@NotNull LeafBlockWrapper block) {
    if (myModifiedBlocksNumber++ >= myBlocksToModifyNumber) {
      return;
    }
    
    update(FormattingStateId.APPLYING_CHANGES, MAX_PROGRESS_VALUE * myModifiedBlocksNumber / myBlocksToModifyNumber);
  }

  @Override
  public void setTask(@Nullable SequentialTask task) {
    myTask = task;
  }

  /**
   * Updates current progress state if necessary.
   * 
   * @param state           current state
   * @param completionRate  completion rate of the given state. Is assumed to belong to <code>[0; 1]</code> interval
   */
  private void update(@NotNull FormattingStateId state, double completionRate) {
    if (myIndicator == null) {
      return;
    }

    updateTextIfNecessary(state);
    
    myLastState = state;
    double newFraction = 0;
    for (FormattingStateId prevState : state.getPreviousStates()) {
      newFraction += MAX_PROGRESS_VALUE * prevState.getProgressWeight() / TOTAL_WEIGHT;
    }
    newFraction += completionRate * state.getProgressWeight() / TOTAL_WEIGHT;
    
    // We don't bother about imprecise floating point arithmetic here because that is enough for progress representation.
    double currentFraction = myIndicator.getFraction();
    if (newFraction - currentFraction < MAX_PROGRESS_VALUE / 100) {
      return;
    }
    
    myIndicator.setFraction(newFraction);
  }
  
  private void updateTextIfNecessary(@NotNull FormattingStateId currentState) {
    if (myLastState != currentState && myIndicator != null) {
      myIndicator.setText(currentState.getDescription());
    }
  }
  
  private class MyCancelCallback implements Runnable {
    @Override
    public void run() {
      FORMATTING_CANCELLED_FLAG.set(true);
      myRunning = false;
      VirtualFile file = myFile.get();
      Document document = myDocument.get();
      if (file == null || document == null || myDocumentModificationStampBefore < 0) {
        return;
      }
      FileEditor editor = FileEditorManager.getInstance(myProject).getSelectedEditor(file);
      if (editor == null) {
        return;
      }
      
      UndoManager manager = UndoManager.getInstance(myProject);
      while (manager.isUndoAvailable(editor) && document.getModificationStamp() != myDocumentModificationStampBefore) {
        manager.undo(editor);
      }
    }
  }
}
