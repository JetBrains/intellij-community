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

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.OffsetMap;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.LanguageLiteralEscapers;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class TemplateState implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.impl.TemplateState");
  private Project myProject;
  private Editor myEditor;

  private TemplateImpl myTemplate;
  private TemplateImpl myPrevTemplate;
  private TemplateSegments mySegments = null;
  private Map<String, String> myPredefinedVariableValues;

  private RangeMarker myTemplateRange = null;
  private final ArrayList<RangeHighlighter> myTabStopHighlighters = new ArrayList<RangeHighlighter>();
  private int myCurrentVariableNumber = -1;
  private int myCurrentSegmentNumber = -1;
  private boolean toProcessTab = true;

  private boolean myDocumentChangesTerminateTemplate = true;
  private boolean myDocumentChanged = false;

  private CommandAdapter myCommandListener;

  private List<TemplateEditingListener> myListeners = new ArrayList<TemplateEditingListener>();
  private DocumentAdapter myEditorDocumentListener;
  private final Map myProperties = new HashMap();
  private boolean myTemplateIndented = false;
  private Document myDocument;
  private boolean myFinished;
  @Nullable private PairProcessor<String, String> myProcessor;

  public TemplateState(@NotNull Project project, final Editor editor) {
    myProject = project;
    myEditor = editor;
    myDocument = myEditor.getDocument();
  }

  private void initListeners() {
    myEditorDocumentListener = new DocumentAdapter() {
      public void beforeDocumentChange(DocumentEvent e) {
        myDocumentChanged = true;
      }
    };

    myCommandListener = new CommandAdapter() {
      boolean started = false;

      public void commandStarted(CommandEvent event) {
        if (myEditor != null) {
          final int offset = myEditor.getCaretModel().getOffset();
          myDocumentChangesTerminateTemplate = myCurrentSegmentNumber >= 0 &&
                                               (offset < mySegments.getSegmentStart(myCurrentSegmentNumber) ||
                                                offset > mySegments.getSegmentEnd(myCurrentSegmentNumber));
        }
        started = true;
      }

      public void beforeCommandFinished(CommandEvent event) {
        if (started) {
          afterChangedUpdate();
        }
      }
    };

    myDocument.addDocumentListener(myEditorDocumentListener);
    CommandProcessor.getInstance().addCommandListener(myCommandListener);
  }

  public synchronized void dispose() {
    if (myEditorDocumentListener != null) {
      myDocument.removeDocumentListener(myEditorDocumentListener);
      myEditorDocumentListener = null;
    }
    if (myCommandListener != null) {
      CommandProcessor.getInstance().removeCommandListener(myCommandListener);
      myCommandListener = null;
    }

    myProcessor = null;

    //Avoid the leak of the editor
    releaseEditor();
    myDocument = null;
  }

  public boolean isToProcessTab() {
    return toProcessTab;
  }

  private void setCurrentVariableNumber(int variableNumber) {
    myCurrentVariableNumber = variableNumber;
    final boolean isFinished = variableNumber < 0;
    ((DocumentEx)myDocument).setStripTrailingSpacesEnabled(isFinished);
    myCurrentSegmentNumber = isFinished ? -1 : getCurrentSegmentNumber();
  }

  @Nullable
  public String getTrimmedVariableValue(int variableIndex) {
    final TextResult value = getVariableValue(myTemplate.getVariableNameAt(variableIndex));
    return value == null ? null : value.getText().trim();
  }

  @Nullable
  public TextResult getVariableValue(@NotNull String variableName) {
    if (variableName.equals(TemplateImpl.SELECTION)) {
      final String selection = (String)getProperties().get(ExpressionContext.SELECTION);
      return new TextResult(selection == null ? "" : selection);
    }
    if (variableName.equals(TemplateImpl.END)) {
      return new TextResult("");
    }
    if (myPredefinedVariableValues != null && myPredefinedVariableValues.containsKey(variableName)) {
      return new TextResult(myPredefinedVariableValues.get(variableName));
    }
    CharSequence text = myDocument.getCharsSequence();
    int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
    if (segmentNumber < 0) {
      return null;
    }
    int start = mySegments.getSegmentStart(segmentNumber);
    int end = mySegments.getSegmentEnd(segmentNumber);
    int length = myDocument.getTextLength();
    if (start > length || end > length) {
      return null;
    }
    return new TextResult(text.subSequence(start, end).toString());
  }

  @Nullable
  public TextRange getCurrentVariableRange() {
    int number = getCurrentSegmentNumber();
    if (number == -1) return null;
    return new TextRange(mySegments.getSegmentStart(number), mySegments.getSegmentEnd(number));
  }

  @Nullable
  public TextRange getVariableRange(int variableIndex) {
    return getVariableRange(myTemplate.getVariableNameAt(variableIndex));
  }

  @Nullable
  public TextRange getVariableRange(String variableName) {
    int segment = myTemplate.getVariableSegmentNumber(variableName);
    if (segment < 0) return null;

    return new TextRange(mySegments.getSegmentStart(segment), mySegments.getSegmentEnd(segment));
  }

  public boolean isFinished() {
    return myCurrentVariableNumber < 0;
  }

  private void releaseAll() {
    if (mySegments != null) {
      mySegments.removeAll();
      mySegments = null;
    }
    myTemplateRange = null;
    myPrevTemplate = myTemplate;
    myTemplate = null;
    releaseEditor();
    myTabStopHighlighters.clear();
  }

  private void releaseEditor() {
    if (myEditor != null) {
      for (RangeHighlighter segmentHighlighter : myTabStopHighlighters) {
        myEditor.getMarkupModel().removeHighlighter(segmentHighlighter);
      }

      myEditor = null;
    }
  }

  public void start(TemplateImpl template,
                    @Nullable final PairProcessor<String, String> processor,
                    @Nullable Map<String, String> predefinedVarValues) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    myProcessor = processor;

    final DocumentReference[] refs =
      myDocument == null ? null : new DocumentReference[]{DocumentReferenceManager.getInstance().create(myDocument)};

    UndoManager.getInstance(myProject).undoableActionPerformed(new UndoableAction() {
      public void undo() {
        if (myDocument != null) {
          fireTemplateCancelled();
          LookupManager.getInstance(myProject).hideActiveLookup();
          int oldVar = myCurrentVariableNumber;
          setCurrentVariableNumber(-1);
          currentVariableChanged(oldVar);
        }
      }

      public void redo() {
        //TODO:
        // throw new UnexpectedUndoException("Not implemented");
      }

      public DocumentReference[] getAffectedDocuments() {
        return refs;
      }

      public boolean isGlobal() {
        return false;
      }
    });
    myTemplateIndented = false;
    myCurrentVariableNumber = -1;
    mySegments = new TemplateSegments(myEditor);
    myPrevTemplate = myTemplate;
    myTemplate = template;
    //myArgument = argument;
    myPredefinedVariableValues = predefinedVarValues;

    if (template.isInline()) {
      int caretOffset = myEditor.getCaretModel().getOffset();
      myTemplateRange = myDocument.createRangeMarker(caretOffset, caretOffset + template.getTemplateText().length());
    }
    else {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
      preprocessTemplate(file, myEditor.getCaretModel().getOffset(), myTemplate.getTemplateText());
      int caretOffset = myEditor.getCaretModel().getOffset();
      myTemplateRange = myDocument.createRangeMarker(caretOffset, caretOffset);
    }
    myTemplateRange.setGreedyToLeft(true);
    myTemplateRange.setGreedyToRight(true);

    processAllExpressions(template);
  }

  private void fireTemplateCancelled() {
    if (myFinished) return;
    myFinished = true;
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.templateCancelled(myTemplate);
    }
  }

  private void preprocessTemplate(final PsiFile file, int caretOffset, final String textToInsert) {
    for (TemplatePreprocessor preprocessor : Extensions.getExtensions(TemplatePreprocessor.EP_NAME)) {
      preprocessor.preprocessTemplate(myEditor, file, caretOffset, textToInsert, myTemplate.getTemplateText());
    }
  }

  private void processAllExpressions(final TemplateImpl template) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (!template.isInline()) myDocument.insertString(myTemplateRange.getStartOffset(), template.getTemplateText());
        for (int i = 0; i < template.getSegmentsCount(); i++) {
          int segmentOffset = myTemplateRange.getStartOffset() + template.getSegmentOffset(i);
          mySegments.addSegment(segmentOffset, segmentOffset);
        }

        calcResults(false);
        calcResults(false);  //Fixed SCR #[vk500] : all variables should be recalced twice on start.
        doReformat(null);

        int nextVariableNumber = getNextVariableNumber(-1);

        if (nextVariableNumber >= 0) {
          fireWaitingForInput();
        }

        if (nextVariableNumber == -1) {
          finishTemplateEditing(false);
        }
        else {
          setCurrentVariableNumber(nextVariableNumber);
          initTabStopHighlighters();
          initListeners();
          focusCurrentExpression();
          currentVariableChanged(-1);
        }
      }
    });
  }

  public void doReformat(final TextRange range) {
    RangeMarker rangeMarker = null;
    if (range != null) {
      rangeMarker = myDocument.createRangeMarker(range);
      rangeMarker.setGreedyToLeft(true);
      rangeMarker.setGreedyToRight(true);
    }
    final RangeMarker finalRangeMarker = rangeMarker;
    final Runnable action = new Runnable() {
      public void run() {
        IntArrayList indices = initEmptyVariables();
        mySegments.setSegmentsGreedy(false);
        reformat(finalRangeMarker);
        mySegments.setSegmentsGreedy(true);
        restoreEmptyVariables(indices);
      }
    };
    ApplicationManager.getApplication().runWriteAction(action);
  }

  private void shortenReferences() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
        if (file != null) {
          IntArrayList indices = initEmptyVariables();
          mySegments.setSegmentsGreedy(false);
          for (TemplateOptionalProcessor processor : Extensions.getExtensions(TemplateOptionalProcessor.EP_NAME)) {
            processor.processText(myProject, myTemplate, myDocument, myTemplateRange, myEditor);
          }
          mySegments.setSegmentsGreedy(true);
          restoreEmptyVariables(indices);
        }
      }
    });
  }

  private void afterChangedUpdate() {
    if (isFinished()) return;
    LOG.assertTrue(myTemplate != null, myPrevTemplate != null ? myPrevTemplate.getKey() : "prev template is null");
    if (myDocumentChanged) {
      if (myDocumentChangesTerminateTemplate || mySegments.isInvalid()) {
        final int oldIndex = myCurrentVariableNumber;
        setCurrentVariableNumber(-1);
        currentVariableChanged(oldIndex);
        fireTemplateCancelled();
      }
      else {
        calcResults(true);
      }
      myDocumentChanged = false;
    }
  }

  private String getExpressionString(int index) {
    CharSequence text = myDocument.getCharsSequence();

    if (!mySegments.isValid(index)) return "";

    int start = mySegments.getSegmentStart(index);
    int end = mySegments.getSegmentEnd(index);

    return text.subSequence(start, end).toString();
  }

  private int getCurrentSegmentNumber() {
    if (myCurrentVariableNumber == -1) {
      return -1;
    }
    String variableName = myTemplate.getVariableNameAt(myCurrentVariableNumber);
    return myTemplate.getVariableSegmentNumber(variableName);
  }

  private void focusCurrentExpression() {
    if (isFinished()) {
      return;
    }

    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);

    final int currentSegmentNumber = getCurrentSegmentNumber();

    lockSegmentAtTheSameOffsetIfAny();

    if (currentSegmentNumber < 0) return;
    final int start = mySegments.getSegmentStart(currentSegmentNumber);
    final int end = mySegments.getSegmentEnd(currentSegmentNumber);
    myEditor.getCaretModel().moveToOffset(end);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();


    myEditor.getSelectionModel().setSelection(start, end);
    Expression expressionNode = myTemplate.getExpressionAt(myCurrentVariableNumber);

    final ExpressionContext context = createExpressionContext(start);
    final LookupElement[] lookupItems = expressionNode.calculateLookupItems(context);
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (lookupItems != null && lookupItems.length > 0) {
      if (((TemplateManagerImpl)TemplateManager.getInstance(myProject)).shouldSkipInTests()) {
        final String s = lookupItems[0].getLookupString();
        EditorModificationUtil.insertStringAtCaret(myEditor, s);
        itemSelected(lookupItems[0], psiFile, currentSegmentNumber, ' ', lookupItems);
      }
      else {
        runLookup(currentSegmentNumber, lookupItems, psiFile);
      }
    }
    else {
      Result result = expressionNode.calculateResult(context);
      if (result != null) {
        result.handleFocused(psiFile, myDocument, mySegments.getSegmentStart(currentSegmentNumber),
                             mySegments.getSegmentEnd(currentSegmentNumber));
      }
    }
    focusCurrentHighlighter(true);
  }

  private void runLookup(final int currentSegmentNumber, final LookupElement[] lookupItems, final PsiFile psiFile) {
    if (myEditor == null) return;

    final LookupManager lookupManager = LookupManager.getInstance(myProject);
    if (lookupManager.isDisposed()) return;

    final Lookup lookup = lookupManager.showLookup(myEditor, lookupItems);
    toProcessTab = false;
    lookup.addLookupListener(new LookupAdapter() {
      public void lookupCanceled(LookupEvent event) {
        lookup.removeLookupListener(this);
        toProcessTab = true;
      }

      public void itemSelected(LookupEvent event) {
        lookup.removeLookupListener(this);
        if (isFinished()) return;
        toProcessTab = true;

        TemplateState.this.itemSelected(event.getItem(), psiFile, currentSegmentNumber, event.getCompletionChar(), lookupItems);
      }
    });
  }

  private void itemSelected(final LookupElement item,
                            final PsiFile psiFile,
                            final int currentSegmentNumber,
                            final char completionChar,
                            LookupElement[] elements) {
    if (item != null) {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      final OffsetMap offsetMap = new OffsetMap(myDocument);
      final InsertionContext context = new InsertionContext(offsetMap, (char)0, elements, psiFile, myEditor);
      context.setTailOffset(myEditor.getCaretModel().getOffset());
      offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, context.getTailOffset() - item.getLookupString().length());
      offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, context.getTailOffset());
      offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, context.getTailOffset());

      Integer bracketCount = (Integer)item.getUserData(LookupItem.BRACKETS_COUNT_ATTR);
      if (bracketCount != null) {
        final StringBuilder tail = new StringBuilder();
        for (int i = 0; i < bracketCount.intValue(); i++) {
          tail.append("[]");
        }
        new WriteCommandAction(myProject) {
          protected void run(com.intellij.openapi.application.Result result) throws Throwable {
            EditorModificationUtil.insertStringAtCaret(myEditor, tail.toString());
          }
        }.execute();
        PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      }

      final TemplateLookupSelectionHandler handler =
        item instanceof LookupItem ? ((LookupItem<?>)item).getAttribute(TemplateLookupSelectionHandler.KEY_IN_LOOKUP_ITEM) : null;
      if (handler != null) {
        handler.itemSelected(item, psiFile, myDocument, mySegments.getSegmentStart(currentSegmentNumber),
                             mySegments.getSegmentEnd(currentSegmentNumber));
      }
      else {
        new WriteCommandAction(myProject) {
          protected void run(com.intellij.openapi.application.Result result) throws Throwable {
            item.handleInsert(context);
          }
        }.execute();
      }

      if (completionChar == '.') {
        EditorModificationUtil.insertStringAtCaret(myEditor, ".");
        AutoPopupController.getInstance(myProject).autoPopupMemberLookup(myEditor, null);
        return;
      }

      if (!isFinished()) {
        calcResults(true);
      }
    }

    nextTab();
  }

  private void unblockDocument() {
    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myDocument);
  }

  private void calcResults(final boolean isQuick) {
    if (myProcessor != null && myCurrentVariableNumber >= 0) {
      final String variableName = myTemplate.getVariableNameAt(myCurrentVariableNumber);
      final TextResult value = getVariableValue(variableName);
      if (value != null && value.getText().length() > 0) {
        if (!myProcessor.process(variableName, value.getText())) {
          finishTemplateEditing(false); // nextTab(); ?
          return;
        }
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        BitSet calcedSegments = new BitSet();
        int maxAttempts = (myTemplate.getVariableCount() + 1) * 3;

        do {
          maxAttempts--;
          calcedSegments.clear();
          for (int i = myCurrentVariableNumber + 1; i < myTemplate.getVariableCount(); i++) {
            String variableName = myTemplate.getVariableNameAt(i);
            int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
            if (segmentNumber < 0) continue;
            Expression expression = myTemplate.getExpressionAt(i);
            Expression defaultValue = myTemplate.getDefaultValueAt(i);
            String oldValue = getVariableValue(variableName).getText();
            recalcSegment(segmentNumber, isQuick, expression, defaultValue);
            final TextResult value = getVariableValue(variableName);
            assert value != null : "name=" + variableName + "\ntext=" + myTemplate.getTemplateText();
            String newValue = value.getText();
            if (!newValue.equals(oldValue)) {
              calcedSegments.set(segmentNumber);
            }
          }

          for (int i = 0; i < myTemplate.getSegmentsCount(); i++) {
            if (!calcedSegments.get(i)) {
              String variableName = myTemplate.getSegmentName(i);
              String newValue = getVariableValue(variableName).getText();
              int start = mySegments.getSegmentStart(i);
              int end = mySegments.getSegmentEnd(i);
              replaceString(newValue, start, end, i);
            }
          }
        }
        while (!calcedSegments.isEmpty() && maxAttempts >= 0);
      }
    });
  }

  private void recalcSegment(int segmentNumber, boolean isQuick, Expression expressionNode, Expression defaultValue) {
    String oldValue = getExpressionString(segmentNumber);
    int start = mySegments.getSegmentStart(segmentNumber);
    int end = mySegments.getSegmentEnd(segmentNumber);
    ExpressionContext context = createExpressionContext(start);
    Result result;
    if (isQuick) {
      result = expressionNode.calculateQuickResult(context);
    }
    else {
      result = expressionNode.calculateResult(context);
      if (expressionNode instanceof ConstantNode) {
        if (result instanceof TextResult) {
          TextResult text = (TextResult)result;
          if (text.getText().length() == 0 && defaultValue != null) {
            result = defaultValue.calculateResult(context);
          }
        }
      }
      if (result == null && defaultValue != null) {
        result = defaultValue.calculateResult(context);
      }
    }
    if (result == null) return;

    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    // psiFile does not contain modifications from document at this point so we need to use template start offset for retrieving anchor 
    PsiElement element = psiFile.findElementAt(myTemplateRange != null ? myTemplateRange.getStartOffset():start);
    if (result.equalsToText(oldValue, element)) return;

    String newValue = result.toString();
    if (newValue == null) newValue = "";

    if (element != null) {
      newValue = LanguageLiteralEscapers.INSTANCE.forLanguage(element.getLanguage()).getEscapedText(element, newValue);
    }

    replaceString(newValue, start, end, segmentNumber);

    if (result instanceof RecalculatableResult) {
      shortenReferences();
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      ((RecalculatableResult)result)
        .handleRecalc(psiFile, myDocument, mySegments.getSegmentStart(segmentNumber), mySegments.getSegmentEnd(segmentNumber));
    }
  }

  private void replaceString(String newValue, int start, int end, int segmentNumber) {
    String oldText = myDocument.getCharsSequence().subSequence(start, end).toString();

    if (!oldText.equals(newValue)) {
      int segmentNumberWithTheSameStart = mySegments.getSegmentWithTheSameStart(segmentNumber, start);
      mySegments.setNeighboursGreedy(segmentNumber, false);
      myDocument.replaceString(start, end, newValue);
      int newEnd = start + newValue.length();
      mySegments.replaceSegmentAt(segmentNumber, start, newEnd);
      mySegments.setNeighboursGreedy(segmentNumber, true);

      if (segmentNumberWithTheSameStart != -1) {
        mySegments.replaceSegmentAt(segmentNumberWithTheSameStart, newEnd,
                                    newEnd + mySegments.getSegmentEnd(segmentNumberWithTheSameStart) -
                                    mySegments.getSegmentStart(segmentNumberWithTheSameStart));
      }
    }
  }

  public void previousTab() {
    if (isFinished()) {
      return;
    }

    myDocumentChangesTerminateTemplate = false;

    final int oldVar = myCurrentVariableNumber;
    int previousVariableNumber = getPreviousVariableNumber(oldVar);
    if (previousVariableNumber >= 0) {
      focusCurrentHighlighter(false);
      calcResults(false);
      doReformat(null);
      setCurrentVariableNumber(previousVariableNumber);
      focusCurrentExpression();
      currentVariableChanged(oldVar);
    }
  }

  public void nextTab() {
    if (isFinished()) {
      return;
    }

    //some psi operations may block the document, unblock here
    unblockDocument();

    myDocumentChangesTerminateTemplate = false;

    final int oldVar = myCurrentVariableNumber;
    int nextVariableNumber = getNextVariableNumber(oldVar);
    if (nextVariableNumber == -1) {
      calcResults(false);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          reformat(null);
        }
      });
      finishTemplateEditing(false);
      return;
    }
    focusCurrentHighlighter(false);
    calcResults(false);
    doReformat(null);
    setCurrentVariableNumber(nextVariableNumber);
    focusCurrentExpression();
    currentVariableChanged(oldVar);
  }

  private void lockSegmentAtTheSameOffsetIfAny() {
    mySegments.lockSegmentAtTheSameOffsetIfAny(getCurrentSegmentNumber());
  }

  private ExpressionContext createExpressionContext(final int start) {
    return new ExpressionContext() {
      public Project getProject() {
        return myProject;
      }

      public Editor getEditor() {
        return myEditor;
      }

      public int getStartOffset() {
        return start;
      }

      public int getTemplateStartOffset() {
        if (myTemplateRange == null) {
          return -1;
        }
        return myTemplateRange.getStartOffset();
      }

      public int getTemplateEndOffset() {
        if (myTemplateRange == null) {
          return -1;
        }
        return myTemplateRange.getEndOffset();
      }

      public <T> T getProperty(Key<T> key) {
        return (T)myProperties.get(key);
      }
    };
  }

  public void gotoEnd(boolean brokenOff) {
    calcResults(false);
    doReformat(null);
    finishTemplateEditing(brokenOff);
  }

  public void gotoEnd() {
    gotoEnd(false);
  }

  private void finishTemplateEditing(boolean brokenOff) {
    if (myTemplate == null) return;

    LookupManager.getInstance(myProject).hideActiveLookup();

    int endSegmentNumber = myTemplate.getEndSegmentNumber();
    int offset = -1;
    if (endSegmentNumber >= 0) {
      offset = mySegments.getSegmentStart(endSegmentNumber);
    }
    else {
      if (!myTemplate.isSelectionTemplate() && !myTemplate.isInline()) { //do not move caret to the end of range for selection templates
        offset = myTemplateRange.getEndOffset();
      }
    }

    if (offset >= 0) {
      myEditor.getCaretModel().moveToOffset(offset);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }

    myEditor.getSelectionModel().removeSelection();
    int selStart = myTemplate.getSelectionStartSegmentNumber();
    int selEnd = myTemplate.getSelectionEndSegmentNumber();
    if (selStart >= 0 && selEnd >= 0) {
      myEditor.getSelectionModel().setSelection(mySegments.getSegmentStart(selStart), mySegments.getSegmentStart(selEnd));
    }
    fireBeforeTemplateFinished();
    final Editor editor = myEditor;
    int oldVar = myCurrentVariableNumber;
    setCurrentVariableNumber(-1);
    currentVariableChanged(oldVar);
    ((TemplateManagerImpl)TemplateManager.getInstance(myProject)).clearTemplateState(editor);
    fireTemplateFinished(brokenOff);
    myListeners.clear();
    myProject = null;
  }

  private int getNextVariableNumber(int currentVariableNumber) {
    for (int i = currentVariableNumber + 1; i < myTemplate.getVariableCount(); i++) {
      if (checkIfTabStop(i)) {
        return i;
      }
    }
    return -1;
  }

  private int getPreviousVariableNumber(int currentVariableNumber) {
    for (int i = currentVariableNumber - 1; i >= 0; i--) {
      if (checkIfTabStop(i)) {
        return i;
      }
    }
    return -1;
  }

  private boolean checkIfTabStop(int currentVariableNumber) {
    Expression expression = myTemplate.getExpressionAt(currentVariableNumber);
    if (expression == null) {
      return false;
    }
    String variableName = myTemplate.getVariableNameAt(currentVariableNumber);
    if (!(myPredefinedVariableValues != null && myPredefinedVariableValues.containsKey(variableName))) {
      if (myTemplate.isAlwaysStopAt(currentVariableNumber)) {
        return true;
      }
    }
    int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
    if (segmentNumber < 0) return false;
    int start = mySegments.getSegmentStart(segmentNumber);
    ExpressionContext context = createExpressionContext(start);
    Result result = expression.calculateResult(context);
    if (result == null) {
      return true;
    }
    LookupElement[] items = expression.calculateLookupItems(context);
    return items != null && items.length > 1;
  }

  private IntArrayList initEmptyVariables() {
    int endSegmentNumber = myTemplate.getEndSegmentNumber();
    int selStart = myTemplate.getSelectionStartSegmentNumber();
    int selEnd = myTemplate.getSelectionEndSegmentNumber();
    IntArrayList indices = new IntArrayList();
    for (int i = 0; i < myTemplate.getSegmentsCount(); i++) {
      int length = mySegments.getSegmentEnd(i) - mySegments.getSegmentStart(i);
      if (length != 0) continue;
      if (i == endSegmentNumber || i == selStart || i == selEnd) continue;

      String name = myTemplate.getSegmentName(i);
      for (int j = 0; j < myTemplate.getVariableCount(); j++) {
        if (myTemplate.getVariableNameAt(j).equals(name)) {
          Expression e = myTemplate.getExpressionAt(j);
          @NonNls String marker = "a";
          if (e instanceof MacroCallNode) {
            marker = ((MacroCallNode)e).getMacro().getDefaultValue();
          }
          int start = mySegments.getSegmentStart(i);
          int end = start + marker.length();
          myDocument.insertString(start, marker);
          mySegments.replaceSegmentAt(i, start, end);
          indices.add(i);
          break;
        }
      }
    }
    return indices;
  }

  private void restoreEmptyVariables(IntArrayList indices) {
    for (int i = 0; i < indices.size(); i++) {
      int index = indices.get(i);
      myDocument.deleteString(mySegments.getSegmentStart(index), mySegments.getSegmentEnd(index));
    }
  }

  private void initTabStopHighlighters() {
    for (int i = 0; i < myTemplate.getVariableCount(); i++) {
      String variableName = myTemplate.getVariableNameAt(i);
      int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
      if (segmentNumber < 0) continue;
      RangeHighlighter segmentHighlighter = getSegmentHighlighter(segmentNumber, false, false);
      myTabStopHighlighters.add(segmentHighlighter);
    }

    int endSegmentNumber = myTemplate.getEndSegmentNumber();
    if (endSegmentNumber >= 0) {
      RangeHighlighter segmentHighlighter = getSegmentHighlighter(endSegmentNumber, false, true);
      myTabStopHighlighters.add(segmentHighlighter);
    }
  }

  private RangeHighlighter getSegmentHighlighter(int segmentNumber, boolean isSelected, boolean isEnd) {
    TextAttributes attributes = isSelected ? new TextAttributes(null, null, Color.red, EffectType.BOXED, Font.PLAIN) : new TextAttributes();
    TextAttributes endAttributes = new TextAttributes();

    int start = mySegments.getSegmentStart(segmentNumber);
    int end = mySegments.getSegmentEnd(segmentNumber);
    RangeHighlighter segmentHighlighter = myEditor.getMarkupModel()
      .addRangeHighlighter(start, end, HighlighterLayer.LAST + 1, isEnd ? endAttributes : attributes, HighlighterTargetArea.EXACT_RANGE);
    segmentHighlighter.setGreedyToLeft(true);
    segmentHighlighter.setGreedyToRight(true);
    return segmentHighlighter;
  }

  private void focusCurrentHighlighter(boolean toSelect) {
    if (isFinished()) {
      return;
    }
    if (myCurrentVariableNumber >= myTabStopHighlighters.size()) {
      return;
    }
    RangeHighlighter segmentHighlighter = myTabStopHighlighters.get(myCurrentVariableNumber);
    if (segmentHighlighter != null) {
      final int segmentNumber = getCurrentSegmentNumber();
      RangeHighlighter newSegmentHighlighter = getSegmentHighlighter(segmentNumber, toSelect, false);
      if (newSegmentHighlighter != null) {
        myEditor.getMarkupModel().removeHighlighter(segmentHighlighter);
        myTabStopHighlighters.set(myCurrentVariableNumber, newSegmentHighlighter);
      }
    }
  }

  private void reformat(RangeMarker rangeMarkerToReformat) {
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (file != null) {
      CodeStyleManager style = CodeStyleManager.getInstance(myProject);
      for (TemplateOptionalProcessor optionalProcessor : Extensions.getExtensions(TemplateOptionalProcessor.EP_NAME)) {
        optionalProcessor.processText(myProject, myTemplate, myDocument, myTemplateRange, myEditor);
      }
      if (myTemplate.isToReformat()) {
        try {
          int endSegmentNumber = myTemplate.getEndSegmentNumber();
          PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
          RangeMarker rangeMarker = null;
          if (endSegmentNumber >= 0) {
            int endVarOffset = mySegments.getSegmentStart(endSegmentNumber);
            PsiElement marker = style.insertNewLineIndentMarker(file, endVarOffset);
            if (marker != null) rangeMarker = myDocument.createRangeMarker(marker.getTextRange());
          }
          int startOffset = rangeMarkerToReformat != null ? rangeMarkerToReformat.getStartOffset() : myTemplateRange.getStartOffset();
          int endOffset = rangeMarkerToReformat != null ? rangeMarkerToReformat.getEndOffset() : myTemplateRange.getEndOffset();
          style.reformatText(file, startOffset, endOffset);
          PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
          PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myDocument);

          if (rangeMarker != null && rangeMarker.isValid()) {
            //[ven] TODO: [max] correct javadoc reformatting to eliminate isValid() check!!!
            mySegments.replaceSegmentAt(endSegmentNumber, rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
            myDocument.deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      else if (myTemplate.isToIndent()) {
        if (!myTemplateIndented) {
          smartIndent(myTemplateRange.getStartOffset(), myTemplateRange.getEndOffset());
          myTemplateIndented = true;
        }
      }
    }
  }

  private void smartIndent(int startOffset, int endOffset) {
    int startLineNum = myDocument.getLineNumber(startOffset);
    int endLineNum = myDocument.getLineNumber(endOffset);
    if (endLineNum == startLineNum) {
      return;
    }

    int indentLineNum = startLineNum;

    int lineLength = 0;
    for (; indentLineNum >= 0; indentLineNum--) {
      lineLength = myDocument.getLineEndOffset(indentLineNum) - myDocument.getLineStartOffset(indentLineNum);
      if (lineLength > 0) {
        break;
      }
    }
    if (indentLineNum < 0) {
      return;
    }
    StringBuilder buffer = new StringBuilder();
    CharSequence text = myDocument.getCharsSequence();
    for (int i = 0; i < lineLength; i++) {
      char ch = text.charAt(myDocument.getLineStartOffset(indentLineNum) + i);
      if (ch != ' ' && ch != '\t') {
        break;
      }
      buffer.append(ch);
    }
    if (buffer.length() == 0) {
      return;
    }
    String stringToInsert = buffer.toString();
    for (int i = startLineNum + 1; i <= endLineNum; i++) {
      myDocument.insertString(myDocument.getLineStartOffset(i), stringToInsert);
    }
  }

  public void addTemplateStateListener(TemplateEditingListener listener) {
    myListeners.add(listener);
  }

  private void fireTemplateFinished(boolean brokenOff) {
    if (myFinished) return;
    myFinished = true;
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.templateFinished(myTemplate, brokenOff);
    }
  }

  private void fireBeforeTemplateFinished() {
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.beforeTemplateFinished(this, myTemplate);
    }
  }

  private void fireWaitingForInput() {
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.waitingForInput(myTemplate);
    }
  }

  private void currentVariableChanged(int oldIndex) {
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.currentVariableChanged(this, myTemplate, oldIndex, myCurrentVariableNumber);
    }
    if (myCurrentSegmentNumber < 0) {
      releaseAll();
    }
  }

  public Map getProperties() {
    return myProperties;
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

}
