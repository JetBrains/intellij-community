package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.DefaultCharFilter;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.jsp.JspUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 *
 */
public class TemplateState implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.impl.TemplateState");
  private Project myProject;
  private Editor myEditor;

  private TemplateImpl myTemplate;
  private TemplateSegments mySegments = null;

  private boolean toProcessChangedUpdate = true;
  private RangeMarker myTemplateRange = null;
  private ArrayList<RangeHighlighter> myTabStopHighlighters = new ArrayList<RangeHighlighter>();
  private int myCurrentVariableNumber = -1;
  private int myCurrentSegmentNumber = -1;
  private boolean toProcessTab = true;
  private boolean myChangesFlag = false;

  private CommandAdapter myCommandListener;

  private List<TemplateEditingListener> myListeners = new ArrayList<TemplateEditingListener>();
  private DocumentAdapter myEditorDocumentListener;
  private Map myProperties = new HashMap();
  private boolean myTemplateIndented = false;
  private CodeStyleManager myCodeStyleManager;
  private Document myDocument;

  private static final String UP_ACTION = ActionsBundle.actionText("EditorUp");
  private static final String DOWN_ACTION = ActionsBundle.actionText("EditorDown");

  public TemplateState(Project project, final Editor editor) {
    myProject = project;
    myEditor = editor;
    myCodeStyleManager = CodeStyleManager.getInstance(project);
    myDocument = myEditor.getDocument();
  }

  private void initListeners() {
    myEditorDocumentListener = new DocumentAdapter() {
      public void beforeDocumentChange(DocumentEvent e) {
        if (!isFinished()) {
          if (toProcessChangedUpdate) {
            UndoManager undoManager = UndoManager.getInstance(myProject);
            if (!undoManager.isUndoInProgress() && !undoManager.isRedoInProgress()) {
              if (myCurrentSegmentNumber >= 0) {
                myChangesFlag = e.getOffset() < mySegments.getSegmentStart(myCurrentSegmentNumber)
                                || e.getOffset() + e.getOldLength() > mySegments.getSegmentEnd(myCurrentSegmentNumber);
              }
            }
          }
        }
      }
    };

    myCommandListener = new CommandAdapter() {
      public void beforeCommandFinished(CommandEvent event) {
        //This is a hack to deal with closing lookup, TODO: remove redundant  string on update
        if (!UP_ACTION.equals(event.getCommandName()) && !DOWN_ACTION.equals(event.getCommandName())) {
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

    //Avoid the leak of the editor
    releaseEditor();
    myDocument = null;
  }

  public boolean isToProcessTab() {
    return toProcessTab;
  }

  private void setCurrentVariableNumber(int variableNumber) {
    int prevSegmentNumber = getCurrentSegmentNumber();
    myCurrentVariableNumber = variableNumber;
    ((DocumentEx)myDocument).setStripTrailingSpacesEnabled(variableNumber < 0);
    if (variableNumber < 0) {
      myCurrentSegmentNumber = -1;
      releaseAll();
    }
    else {
      myCurrentSegmentNumber = getCurrentSegmentNumber();
      if (myCurrentSegmentNumber >= 0) {
        mySegments.setSegmentGreedy(myCurrentSegmentNumber, true);
      }
      if (prevSegmentNumber >= 0) {
        mySegments.setSegmentGreedy(prevSegmentNumber, false);
      }
    }
  }

  public TextResult getVariableValue(String variableName) {
    if (variableName.equals(TemplateImpl.SELECTION)) {
      return new TextResult((String)getProperties().get(ExpressionContext.SELECTION));
    }
    if (variableName.equals(TemplateImpl.END)) {
      return new TextResult("");
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

  public TextRange getCurrentVariableRange() {
    int number = getCurrentSegmentNumber();
    if (number == -1) return null;
    return new TextRange(mySegments.getSegmentStart(number), mySegments.getSegmentEnd(number));
  }

  public TextRange getVariableRange(String variableName) {
    int segment = myTemplate.getVariableSegmentNumber(variableName);
    if (segment < 0) return null;

    return new TextRange(mySegments.getSegmentStart(segment), mySegments.getSegmentEnd(segment));
  }

  public boolean isFinished() {
    return (myCurrentVariableNumber < 0);
  }

  private void releaseAll() {
    if (mySegments != null) {
      mySegments.removeAll();
      mySegments = null;
    }
    myTemplateRange = null;
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

  public void start(TemplateImpl template) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    UndoManager.getInstance(myProject).undoableActionPerformed(
      new UndoableAction() {
        public void undo() throws UnexpectedUndoException {
          if (myDocument != null) {
            fireTemplateCancelled();
            //hack to close lookup if any: TODO lookup API for closing active lookup
            final int segmentNumber = getCurrentSegmentNumber();
            if (segmentNumber >= 0) {
              int offsetToMove = myTemplate.getSegmentOffset(segmentNumber) - 1;
              if (offsetToMove < 0) offsetToMove = myDocument.getTextLength();
              final int oldOffset = myEditor.getCaretModel().getOffset();
              myEditor.getCaretModel().moveToOffset(offsetToMove);
              myEditor.getCaretModel().moveToOffset(oldOffset);
            }
            setCurrentVariableNumber(-1);
          }
        }

        public void redo() throws UnexpectedUndoException {
          //TODO:
          // throw new UnexpectedUndoException("Not implemented");
        }

        public DocumentReference[] getAffectedDocuments() {
          if (myDocument == null) return new DocumentReference[0];
          return new DocumentReference[]{DocumentReferenceByDocument.createDocumentReference(myDocument)};
        }

        public boolean isComplex() {
          return false;
        }
      }
    );
    myTemplateIndented = false;
    myCurrentVariableNumber = -1;
    mySegments = new TemplateSegments(myEditor);
    myTemplate = template;

    preprocessTemplate(PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument), myEditor.getCaretModel().getOffset());
    int caretOffset = myEditor.getCaretModel().getOffset();

    if (template.isInline()) {
      myTemplateRange = myDocument.createRangeMarker(caretOffset, caretOffset + template.getTemplateText().length());
    }
    else {
      myTemplateRange = myDocument.createRangeMarker(caretOffset, caretOffset);
    }
    myTemplateRange.setGreedyToLeft(true);
    myTemplateRange.setGreedyToRight(true);

    processAllExpressions(template);
  }

  private void fireTemplateCancelled() {
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.templateCancelled(myTemplate);
    }
  }

  private void preprocessTemplate(final PsiFile file, int caretOffset) {
    if (PsiUtil.isInJspFile(file)) {
      try {
        caretOffset += JspUtil.escapeCharsInJspContext((PsiUtil.getJspFile(file)), caretOffset, myTemplate.getTemplateText());
        PostprocessReformattingAspect.getInstance(myProject).doPostponedFormatting();
        myEditor.getCaretModel().moveToOffset(caretOffset);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void processAllExpressions(final TemplateImpl template) {
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          toProcessChangedUpdate = false;
          if (!template.isInline()) myDocument.insertString(myTemplateRange.getStartOffset(), template.getTemplateText());
          for (int i = 0; i < template.getSegmentsCount(); i++) {
            int segmentOffset = myTemplateRange.getStartOffset() + template.getSegmentOffset(i);
            mySegments.addSegment(segmentOffset, segmentOffset);
          }

          toProcessChangedUpdate = true;
          calcResults(false);
          calcResults(false);  //Fixed SCR #[vk500] : all variables should be recalced twice on start.
          doReformat();

          int nextVariableNumber = getNextVariableNumber(-1);
          if (nextVariableNumber == -1) {
            finishTemplateEditing();
          }
          else {
            setCurrentVariableNumber(nextVariableNumber);
            initTabStopHighlighters();
            initListeners();
            focusCurrentExpression();
          }
        }
      }
    );
  }

  private void doReformat() {
    final Runnable action = new Runnable() {
      public void run() {
        IntArrayList indices = initEmptyVariables();
        reformat();
        restoreEmptyVariables(indices);
      }
    };
    ApplicationManager.getApplication().runWriteAction(action);
  }

  private void afterChangedUpdate() {
    if (isFinished() || !toProcessChangedUpdate) return;
    LOG.assertTrue(myTemplate != null);
    UndoManager undoManager = UndoManager.getInstance(myProject);
    if (undoManager.isUndoInProgress() || undoManager.isRedoInProgress()) return;

    if (myChangesFlag) {
      setCurrentVariableNumber(-1);
      fireTemplateCancelled();
    }
    else {
      if (!mySegments.isInvalid()) {
        toProcessChangedUpdate = false;
        calcResults(true);
        toProcessChangedUpdate = true;
      }
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

    int currentSegmentNumber = getCurrentSegmentNumber();
    if (currentSegmentNumber < 0) return;
    int start = mySegments.getSegmentStart(currentSegmentNumber);
    final int end = mySegments.getSegmentEnd(currentSegmentNumber);
    myEditor.getCaretModel().moveToOffset(end);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();


    myEditor.getSelectionModel().setSelection(start, end);
    Expression expressionNode = myTemplate.getExpressionAt(myCurrentVariableNumber);

    final ExpressionContext context = createExpressionContext(start);
    final LookupItem[] lookupItems = expressionNode.calculateLookupItems(context);
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (lookupItems != null && lookupItems.length > 0) {
      final LookupItemPreferencePolicy preferencePolicy = new LookupItemPreferencePolicy() {
        public int compare(LookupItem i1, LookupItem i2) {
          if (i1.equals(i2)) return 0;
          if (i1.equals(lookupItems[0])) return -1;
          if (i2.equals(lookupItems[0])) return +1;
          return 0;
        }

        public void setPrefix(String prefix) {
        }

        public void itemSelected(LookupItem item) {
        }
      };

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myEditor == null) return;

          final LookupManager lookupManager = LookupManager.getInstance(myProject);
          if (lookupManager.isDisposed()) return;
          final Lookup lookup = lookupManager.showLookup(myEditor, lookupItems, "", preferencePolicy,
                                                         new DefaultCharFilter(psiFile, end));
          lookup
            .setCurrentItem(
              lookupItems[0]); // [Valentin] not absolutely correct but all existing macros return the first item as the result
          toProcessTab = false;
          lookup.addLookupListener(
            new LookupAdapter() {
              public void lookupCanceled(LookupEvent event) {
                lookup.removeLookupListener(this);
                toProcessTab = true;
              }

              public void itemSelected(LookupEvent event) {
                lookup.removeLookupListener(this);
                if (isFinished()) return;
                toProcessTab = true;

                final LookupItem item = event.getItem();

                if (item != null) {
                  PsiDocumentManager.getInstance(myProject).commitAllDocuments();

                  Integer bracketCount = (Integer)item.getAttribute(LookupItem.BRACKETS_COUNT_ATTR);
                  if (bracketCount != null) {
                    StringBuffer tail = new StringBuffer();
                    for (int i = 0; i < bracketCount.intValue(); i++) {
                      tail.append("[]");
                    }
                    EditorModificationUtil.insertStringAtCaret(myEditor, tail.toString());
                    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
                  }

                  updateTypeBindings(item.getObject(), psiFile, context);

                  char c = event.getCompletionChar();
                  if (c == '.') {
                    EditorModificationUtil.insertStringAtCaret(myEditor, ".");
                    AutoPopupController.getInstance(myProject).autoPopupMemberLookup(myEditor);
                    return;
                  }

                  if (item.getAttribute(Expression.AUTO_POPUP_NEXT_LOOKUP) != null) {
                    AutoPopupController.getInstance(myProject).autoPopupMemberLookup(myEditor);
                    return;
                  }

                  if (!isFinished()) {
                    toProcessChangedUpdate = false;
                    calcResults(true);
                    toProcessChangedUpdate = true;
                  }
                }

                nextTab();
              }
            }
          );
        }
      });
    }
    else {
      Result result = expressionNode.calculateResult(context);
      if (result instanceof PsiElementResult) {
        updateTypeBindings(((PsiElementResult)result).getElement(), psiFile, context);
      }
      if (result instanceof PsiTypeResult) {
        updateTypeBindings(((PsiTypeResult)result).getType(), psiFile, context);
      }
      if (result instanceof InvokeActionResult) {
        ((InvokeActionResult)result).getAction().run();
      }
    }
    focusCurrentHighlighter(true);
  }


  private void updateTypeBindings(Object item, PsiFile file, ExpressionContext context) {
    PsiClass aClass = null;
    if (item instanceof PsiClass) {
      aClass = (PsiClass)item;
    }
    else if (item instanceof PsiType) {
      aClass = PsiUtil.resolveClassInType(((PsiType)item));
    }

    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) {
        if (((PsiTypeParameter)aClass).getOwner() instanceof PsiMethod) {
          int start = context.getStartOffset();
          PsiElement element = file.findElementAt(start);
          PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
          if (method != null) {
            PsiTypeParameterList paramList = method.getTypeParameterList();
            PsiTypeParameter[] params = paramList.getTypeParameters();
            for (PsiTypeParameter param : params) {
              if (param.getName().equals(aClass.getName())) return;
            }
            try {
              toProcessChangedUpdate = false;
              paramList.add(aClass.copy());
              CodeInsightUtil.forcePsiPosprocessAndRestoreElement(paramList);
              toProcessChangedUpdate = true;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
      else {
        TextRange range = getCurrentVariableRange();
        if (range != null) {
          addImportForClass(aClass, range.getStartOffset(), range.getEndOffset());
        }
      }
    }
  }


  private void calcResults(final boolean isQuick) {
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          BitSet calcedSegments = new BitSet();

          do {
            calcedSegments.clear();
            for (int i = myCurrentVariableNumber + 1; i < myTemplate.getVariableCount(); i++) {
              String variableName = myTemplate.getVariableNameAt(i);
              int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
              if (segmentNumber < 0) continue;
              Expression expression = myTemplate.getExpressionAt(i);
              Expression defaultValue = myTemplate.getDefaultValueAt(i);
              String oldValue = getVariableValue(variableName).getText();
              recalcSegment(segmentNumber, isQuick, expression, defaultValue);
              String newValue = getVariableValue(variableName).getText();
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
                toProcessChangedUpdate = false;
                replaceString(newValue, start, end, i);
                toProcessChangedUpdate = true;
              }
            }
          }
          while (!calcedSegments.isEmpty());
        }
      }
    );
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
          if (text.getText().equals("") && defaultValue != null) {
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
    PsiElement element = psiFile.findElementAt(start);
    if (result.equalsToText(oldValue, element)) return;

    String newValue = result.toString();
    if (newValue == null) newValue = "";

    if (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.STRING_LITERAL) {
      newValue = StringUtil.escapeStringCharacters(newValue);
    }

    toProcessChangedUpdate = false;
    replaceString(newValue, start, end, segmentNumber);

    if (result instanceof PsiTypeResult) {
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      PsiTypeElement t = PsiTreeUtil.getParentOfType(psiFile.findElementAt(start), PsiTypeElement.class);
      if (t != null && t.getTextRange().getStartOffset() == start) {
        try {
          PsiJavaCodeReferenceElement ref = t.getInnermostComponentReferenceElement();
          if (ref != null) {
            myCodeStyleManager.shortenClassReferences(ref);
            PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myDocument);
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (result instanceof PsiTypeResult) {
        updateTypeBindings(((PsiTypeResult)result).getType(), psiFile, context);
      }
      else if (result instanceof PsiClass) updateTypeBindings(result, psiFile, context);
    }

    toProcessChangedUpdate = true;
  }

  private void replaceString(String newValue, int start, int end, int segmentNumber) {
    String oldText = myDocument.getCharsSequence().subSequence(start, end).toString();
    if (!oldText.equals(newValue)) {
      myDocument.replaceString(start, end, newValue);
      mySegments.replaceSegmentAt(segmentNumber, start, start + newValue.length());
    }
  }

  public void previousTab() {
    if (isFinished()) {
      return;
    }
    int previousVariableNumber = getPreviousVariableNumber(myCurrentVariableNumber);
    if (previousVariableNumber >= 0) {
      focusCurrentHighlighter(false);
      calcResults(false);
      doReformat();
      setCurrentVariableNumber(previousVariableNumber);
      focusCurrentExpression();
    }
  }

  public void nextTab() {
    if (isFinished()) {
      return;
    }
    int nextVariableNumber = getNextVariableNumber(myCurrentVariableNumber);
    if (nextVariableNumber == -1) {
      calcResults(false);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          reformat();
        }
      });
      finishTemplateEditing();
      return;
    }
    focusCurrentHighlighter(false);
    calcResults(false);
    doReformat();
    setCurrentVariableNumber(nextVariableNumber);
    focusCurrentExpression();
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

      public Map getProperties() {
        return myProperties;
      }
    };
  }

  public void gotoEnd() {
    calcResults(false);
    doReformat();
    finishTemplateEditing();
  }

  private void finishTemplateEditing() {
    if (myTemplate == null) return;
    int endSegmentNumber = myTemplate.getEndSegmentNumber();
    int offset = -1;
    if (endSegmentNumber >= 0) {
      offset = mySegments.getSegmentStart(endSegmentNumber);
    } else {
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
      myEditor.getSelectionModel().setSelection(
        mySegments.getSegmentStart(selStart),
        mySegments.getSegmentStart(selEnd)
      );
    }

    fireTemplateFinished();
    myListeners.clear();
    setCurrentVariableNumber(-1);
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
    if (myTemplate.isAlwaysStopAt(currentVariableNumber)) {
      return true;
    }
    String variableName = myTemplate.getVariableNameAt(currentVariableNumber);
    int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
    if (segmentNumber <= 0) return false;
    int start = mySegments.getSegmentStart(segmentNumber);
    ExpressionContext context = createExpressionContext(start);
    Result result = expression.calculateResult(context);
    if (result == null) {
      return true;
    }
    LookupItem[] items = expression.calculateLookupItems(context);
    if (items == null) return false;
    return items.length > 1;
  }

  private IntArrayList initEmptyVariables() {
    int endSegmentNumber = myTemplate.getEndSegmentNumber();
    int selStart = myTemplate.getSelectionStartSegmentNumber();
    int selEnd = myTemplate.getSelectionEndSegmentNumber();
    toProcessChangedUpdate = false;
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
          myDocument.insertString(start, marker);
          mySegments.replaceSegmentAt(i, start, start + marker.length());
          indices.add(i);
          break;
        }
      }
    }
    toProcessChangedUpdate = true;
    return indices;
  }

  private void restoreEmptyVariables(IntArrayList indices) {
    toProcessChangedUpdate = false;
    for (int i = 0; i < indices.size(); i++) {
      int index = indices.get(i);

      String name = myTemplate.getSegmentName(index);
      for (int j = 0; j < myTemplate.getVariableCount(); j++) {
        if (myTemplate.getVariableNameAt(j).equals(name)) {
          Expression e = myTemplate.getExpressionAt(j);
          @NonNls String marker = "a"; //was default
          if (e instanceof MacroCallNode) {
            marker = ((MacroCallNode)e).getMacro().getDefaultValue();
          }
          myDocument.deleteString(mySegments.getSegmentStart(index), mySegments.getSegmentStart(index) + marker.length());
          break;
        }
      }
    }
    toProcessChangedUpdate = true;
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
    TextAttributes attributes = isSelected
                                ? new TextAttributes(null, null, Color.red, EffectType.BOXED, TextAttributes.TRANSPARENT)
                                : new TextAttributes();
    TextAttributes endAttributes = new TextAttributes();

    RangeHighlighter segmentHighlighter;
    int start = mySegments.getSegmentStart(segmentNumber);
    int end = mySegments.getSegmentEnd(segmentNumber);
    if (isEnd) {
      segmentHighlighter = myEditor.getMarkupModel()
        .addRangeHighlighter(start, end, HighlighterLayer.LAST + 1, endAttributes, HighlighterTargetArea.EXACT_RANGE);
    }
    else {
      segmentHighlighter = myEditor.getMarkupModel()
        .addRangeHighlighter(start, end, HighlighterLayer.LAST + 1, attributes, HighlighterTargetArea.EXACT_RANGE);
    }
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
      RangeHighlighter newSegmentHighlighter = getSegmentHighlighter(getCurrentSegmentNumber(), toSelect, false);
      if (newSegmentHighlighter != null) {
        myEditor.getMarkupModel().removeHighlighter(segmentHighlighter);
        myTabStopHighlighters.set(myCurrentVariableNumber, newSegmentHighlighter);
      }
    }
  }

  private void reformat() {
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (file != null) {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
      if (myTemplate.isToShortenLongNames()) {
        try {
          toProcessChangedUpdate = false;
          PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
          codeStyleManager.shortenClassReferences(file, myTemplateRange.getStartOffset(), myTemplateRange.getEndOffset());
          PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myDocument);
          toProcessChangedUpdate = true;
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      if (myTemplate.isToReformat()) {
        try {
          toProcessChangedUpdate = false;
          int endSegmentNumber = myTemplate.getEndSegmentNumber();
          PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
          PsiElement marker = null;
          RangeMarker rangeMarker = null;
          if (endSegmentNumber >= 0) {
            int endVarOffset = mySegments.getSegmentStart(endSegmentNumber);
            marker = codeStyleManager.insertNewLineIndentMarker(file, endVarOffset);
            if(marker != null) rangeMarker = myDocument.createRangeMarker(marker.getTextRange());
          }
          codeStyleManager.reformatText(file, myTemplateRange.getStartOffset(), myTemplateRange.getEndOffset());
          PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);

          if (rangeMarker != null && rangeMarker.isValid()) {
            //[ven] TODO: [max] correct javadoc reformatting to eliminate isValid() check!!!
            mySegments.replaceSegmentAt(endSegmentNumber, rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
            myDocument.deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
          }
          toProcessChangedUpdate = true;
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
    StringBuffer buffer = new StringBuffer();
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

  private void fireTemplateFinished() {
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.templateFinished(myTemplate);
    }
  }

  private void addImportForClass(final PsiClass aClass, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    if (!aClass.isValid() || aClass.getQualifiedName() == null) return;

    PsiManager manager = PsiManager.getInstance(myProject);
    PsiResolveHelper helper = manager.getResolveHelper();

    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    CharSequence chars = myDocument.getCharsSequence();

    PsiElement element = file.findElementAt(startOffset);
    String refText = chars.subSequence(startOffset, endOffset).toString();
    PsiClass refClass = helper.resolveReferencedClass(refText, element);
    if (aClass.equals(refClass)) return;

    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      while (parent != null) {
        PsiElement tmp = parent.getParent();
        if (!(tmp instanceof PsiJavaCodeReferenceElement) || tmp.getTextRange().getEndOffset() > endOffset) break;
        parent = tmp;
      }
      if (parent instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement)parent).isQualified()) {
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              ref.bindToElement(aClass);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }
  }

  public Map getProperties() {
    return myProperties;
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  void reset() {
    myListeners = new ArrayList<TemplateEditingListener>();
  }
}
