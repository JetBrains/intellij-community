// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduce.inplace;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.*;

public abstract class AbstractInplaceIntroducer<V extends PsiNameIdentifierOwner, E extends PsiElement> extends
                                                                                                        InplaceVariableIntroducer<E> {
  protected V myLocalVariable;
  protected RangeMarker myLocalMarker;

  protected final String myExprText;
  private final String myLocalName;

  public static final Key<AbstractInplaceIntroducer> ACTIVE_INTRODUCE = Key.create("ACTIVE_INTRODUCE");

  private EditorEx myPreview;
  private final JComponent myPreviewComponent;

  private DocumentListener myDocumentAdapter;
  protected final JPanel myWholePanel;
  protected boolean myFinished = false;

  public AbstractInplaceIntroducer(Project project,
                                   Editor editor,
                                   @Nullable E expr,
                                   @Nullable V localVariable,
                                   E[] occurrences,
                                   @NlsContexts.Command String title,
                                   final FileType languageFileType) {
    super(null, editor, project, title, occurrences, expr);
    myLocalVariable = localVariable;
    if (localVariable != null) {
      final PsiElement nameIdentifier = localVariable.getNameIdentifier();
      if (nameIdentifier != null) {
        myLocalMarker = createMarker(nameIdentifier);
      }
    }
    else {
      myLocalMarker = null;
    }
    myExprText = getExpressionText(expr);
    myLocalName = localVariable != null ? localVariable.getName() : null;

    myPreview = createPreviewComponent(project, languageFileType);
    myPreviewComponent = new JPanel(new BorderLayout());
    myPreviewComponent.add(myPreview.getComponent(), BorderLayout.CENTER);
    myPreviewComponent.setBorder(new EmptyBorder(2, 2, 6, 2));

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(null);

    showDialogAdvertisement(getActionName());
  }

  @Nullable
  protected String getExpressionText(E expr) {
    return expr != null ? expr.getText() : null;
  }

  protected final void setPreviewText(final String text) {
    if (myPreview == null) return; //already disposed
    ApplicationManager.getApplication().runWriteAction(() -> myPreview.getDocument().replaceString(0, myPreview.getDocument().getTextLength(), text));
  }

  protected final JComponent getPreviewComponent() {
    return myPreviewComponent;
  }

  protected final Editor getPreviewEditor() {
    return myPreview;
  }


  @Override
  protected StartMarkAction startRename() throws StartMarkAction.AlreadyStartedException {
    return StartMarkAction.start(myEditor, myProject, getCommandName());
  }

  /**
   * Returns ID of the action the shortcut of which is used to show the non-in-place refactoring dialog.
   *
   * @return action ID
   */
  protected abstract @Nullable @NonNls String getActionName();

  /**
   * Creates an initial version of the declaration for the introduced element. Note that this method is not called in a write action
   * and most likely needs to create one itself.
   *
   * @param replaceAll whether all occurrences are going to be replaced
   * @param names      the suggested names for the declaration
   * @return the declaration
   */
  @Nullable
  protected abstract V createFieldToStartTemplateOn(boolean replaceAll, String @NotNull [] names);

  /**
   * Returns the suggested names for the introduced element.
   *
   * @param replaceAll whether all occurrences are going to be replaced
   * @param variable   introduced element declaration, if already created.
   * @return the suggested names
   */
  protected abstract String @NotNull [] suggestNames(boolean replaceAll, @Nullable V variable);

  protected abstract void performIntroduce();
  protected void performPostIntroduceTasks() {}

  public abstract boolean isReplaceAllOccurrences();
  public abstract void setReplaceAllOccurrences(boolean allOccurrences);
  @Override
  @Nullable
  protected abstract JComponent getComponent();

  protected abstract void saveSettings(@NotNull V variable);
  @Override
  @Nullable
  protected abstract V getVariable();

  public abstract E restoreExpression(@NotNull PsiFile containingFile, @NotNull V variable, @NotNull RangeMarker marker, @Nullable String exprText);

  /**
   * Begins the in-place refactoring operation.
   *
   * @return true if the in-place refactoring was successfully started, false if it failed to start and a dialog should be shown instead.
   */
  public boolean startInplaceIntroduceTemplate() {
    final boolean replaceAllOccurrences = isReplaceAllOccurrences();
    final Ref<Boolean> result = new Ref<>(false);
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final String[] names = suggestNames(replaceAllOccurrences, getLocalVariable());
      boolean started = false;
      try {
        if (replaceAllOccurrences) {
          int segmentsLimit = Registry.intValue("inplace.rename.segments.limit", -1);
          // Too many occurrences: rename template won't start, see InplaceRefactoring.buildTemplateAndStart
          // 2 = variable type + variable name
          if (segmentsLimit != -1 && getOccurrences().length + 2 > segmentsLimit) return;
        }
        final V variable = createFieldToStartTemplateOn(replaceAllOccurrences, names);
        if (variable != null) {
          int caretOffset = getCaretOffset();
          myEditor.getCaretModel().moveToOffset(caretOffset);
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
  
          final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<>();
          nameSuggestions.add(variable.getName());
          nameSuggestions.addAll(Arrays.asList(names));
          initOccurrencesMarkers();
          setElementToRename(variable);
          updateTitle(getVariable());
          started = super.performInplaceRefactoring(nameSuggestions);
          if (started) {
            myDocumentAdapter = new DocumentListener() {
              @Override
              public void documentChanged(@NotNull DocumentEvent e) {
                if (myPreview == null) return;
                final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
                if (templateState != null) {
                  final TextResult value = templateState.getVariableValue(InplaceRefactoring.PRIMARY_VARIABLE_NAME);
                  if (value != null) {
                    updateTitle(getVariable(), value.getText());
                  }
                }
              }
            };
            myEditor.getDocument().addDocumentListener(myDocumentAdapter);
            updateTitle(getVariable());
            if (TemplateManagerImpl.getTemplateState(myEditor) != null) {
              myEditor.putUserData(ACTIVE_INTRODUCE, this);
            }
          }
        }
        result.set(started);
      }
      finally {
        if (!started) {
          finish(true);
        }
      }
    }, getCommandName(), getCommandName());
    return result.get();
  }

  protected int getCaretOffset() {
    RangeMarker r;
    if (myLocalMarker != null) {
      final PsiReference reference = myExpr != null ? myExpr.getReference() : null;
      if (reference != null && reference.resolve() == myLocalVariable) {
        r = myExprMarker;
      } else {
        r = myLocalMarker;
      }
    }
    else {
      r = myExprMarker;
    }
    return r != null ? r.getStartOffset() : 0;
  }

  protected void updateTitle(@Nullable V variable, String value) {
    if (variable == null) return;

    final String variableText = variable.getText();
    final PsiElement identifier = variable.getNameIdentifier();
    if (identifier != null) {
      final int startOffsetInParent = identifier.getStartOffsetInParent();
      setPreviewText(variableText.substring(0, startOffsetInParent) + value + variableText.substring(startOffsetInParent + identifier.getTextLength()));
    } else {
      setPreviewText(variableText.replaceFirst(variable.getName(), value));
    }
    revalidate();
  }

  protected void updateTitle(@Nullable V variable) {
    if (variable == null) return;
    setPreviewText(variable.getText());
    revalidate();
  }

  protected void revalidate() {
    myWholePanel.revalidate();
    if (myTarget != null) {
      myBalloon.revalidate(new PositionTracker.Static<>(myTarget));
    }
  }

  private RangeMarker mySelectedRange = null;
  private boolean myShouldSelect = true;
  @Override
  protected boolean shouldSelectAll() {
    return myShouldSelect;
  }

  public void restartInplaceIntroduceTemplate() {
    Runnable restartTemplateRunnable = () -> {
      final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
      if (templateState != null) {
        myEditor.putUserData(INTRODUCE_RESTART, true);
        try {
          SelectionModel selectionModel = myEditor.getSelectionModel();
          if (selectionModel.hasSelection()) {
            mySelectedRange = myEditor.getDocument().createRangeMarker(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
            mySelectedRange.setGreedyToRight(true);
            mySelectedRange.setGreedyToLeft(true);
          }
          else {
            mySelectedRange = null;
          }
          final TextRange range = templateState.getCurrentVariableRange();
          if (range != null) {
            final TextResult inputText = templateState.getVariableValue(PRIMARY_VARIABLE_NAME);
            final String inputName = inputText != null ? inputText.getText() : null;
            final V variable = getVariable();
            if (inputName == null || variable == null || !isIdentifier(inputName, variable.getLanguage())) {
              final String[] names = suggestNames(isReplaceAllOccurrences(), getLocalVariable());
              ApplicationManager.getApplication().runWriteAction(() -> myEditor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), names[0]));
            }
          }
          templateState.gotoEnd(true);
          try {
            myShouldSelect = false;
            startInplaceIntroduceTemplate();
          }
          finally {
            if (mySelectedRange != null) {
              mySelectedRange.dispose();
              mySelectedRange = null;
            }
            myShouldSelect = true;
          }
        }
        finally {
          myEditor.putUserData(INTRODUCE_RESTART, false);
        }
      }
      updateTitle(getVariable());
    };
    CommandProcessor.getInstance().executeCommand(myProject, restartTemplateRunnable, getCommandName(), getCommandName());
  }

  @Override
  protected void restoreSelection() {
    if (!shouldSelectAll()) {
      if (mySelectedRange != null) {
        VariableInplaceRenamer.restoreSelection(myEditor, mySelectedRange.getTextRange());
      }
      else {
        myEditor.getSelectionModel().removeSelection();
      }
    }
  }

  public String getInputName() {
    return myInsertedName;
  }


  @Override
  public void finish(boolean success) {
    myFinished = true;
    myEditor.putUserData(ACTIVE_INTRODUCE, null);
    if (myDocumentAdapter != null) {
      myEditor.getDocument().removeDocumentListener(myDocumentAdapter);
    }
    if (myBalloon == null) {
      releaseIfNotRestart();
    }
    super.finish(success);
    if (success) {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      final V variable = getVariable();
      if (variable == null) {
        return;
      }
      restoreState(variable);
    }
  }

  @Override
  protected void releaseResources() {
    super.releaseResources();
    if (myPreview == null) return;

    EditorFactory.getInstance().releaseEditor(myPreview);
    myPreview = null;
  }

  @Override
  protected void addReferenceAtCaret(Collection<? super PsiReference> refs) {
    final V variable = ApplicationManager.getApplication().runReadAction((Computable<V>)() -> getLocalVariable());
    if (variable != null) {
      for (PsiReference reference : ReferencesSearch.search(variable)) {
        ProgressManager.checkCanceled();
        refs.add(reference);
      }
    } else {
      refs.clear();
    }
  }

  @Override
  protected void collectAdditionalElementsToRename(@NotNull List<? super Pair<PsiElement, TextRange>> stringUsages) {
    if (isReplaceAllOccurrences()) {
      for (E expression : getOccurrences()) {
        PsiUtilCore.ensureValid(expression);
        stringUsages.add(Pair.create(expression, new TextRange(0, expression.getTextLength())));
      }
    }  else if (getExpr() != null) {
      correctExpression();
      final E expr = getExpr();
      PsiUtilCore.ensureValid(expr);
      stringUsages.add(Pair.create(expr, new TextRange(0, expr.getTextLength())));
    }

    final V localVariable = getLocalVariable();
    if (localVariable != null) {
      final PsiElement nameIdentifier = localVariable.getNameIdentifier();
      if (nameIdentifier != null) {
        int length = nameIdentifier.getTextLength();
        stringUsages.add(Pair.create(nameIdentifier, new TextRange(0, length)));
      }
    }
  }

  protected void correctExpression() {}

  @Override
  protected void addHighlights(@NotNull Map<TextRange, TextAttributes> ranges,
                               @NotNull Editor editor,
                               @NotNull Collection<RangeHighlighter> highlighters,
                               @NotNull HighlightManager highlightManager) {
    final TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final V variable = getVariable();
    if (variable != null) {
      final String name = variable.getName();
      LOG.assertTrue(name != null, variable);
      final int variableNameLength = name.length();
      if (isReplaceAllOccurrences()) {
        for (RangeMarker marker : getOccurrenceMarkers()) {
          final int startOffset = adjustHighlightsStartOffset(marker.getStartOffset());
          highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + variableNameLength, attributes, 0, highlighters, null);
        }
      }
      else if (getExpr() != null) {
        final int startOffset = adjustHighlightsStartOffset(getExprMarker().getStartOffset());
        highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + variableNameLength, attributes, 0, highlighters, null);
      }
    }

    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
  }

  protected int adjustHighlightsStartOffset(int offset) {
    return offset;
  }

  protected void restoreState(@NotNull final V psiField) {
    if (!ReadonlyStatusHandler.ensureDocumentWritable(myProject, InjectedLanguageEditorUtil.getTopLevelEditor(myEditor).getDocument())) return;
    PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(
      () -> ApplicationManager.getApplication().runWriteAction(() -> doRestoreState(psiField)));
  }

  private void doRestoreState(@NotNull V psiField) {
    final PsiFile containingFile = psiField.getContainingFile();
    final RangeMarker exprMarker = getExprMarker();
    if (exprMarker != null) {
      myExpr = restoreExpression(containingFile, psiField, exprMarker, myExprText);
    }

    if (myLocalMarker != null) {
      final PsiElement refVariableElement = containingFile.findElementAt(myLocalMarker.getStartOffset());
      if (refVariableElement != null) {
        final PsiElement parent = refVariableElement.getParent();
        if (parent instanceof PsiNamedElement) {
          ((PsiNamedElement)parent).setName(myLocalName);
        }
      }

      final V localVariable = getLocalVariable();
      if (localVariable != null && localVariable.isPhysical()) {
        myLocalVariable = localVariable;
        final PsiElement nameIdentifier = localVariable.getNameIdentifier();
        if (nameIdentifier != null) {
          myLocalMarker = createMarker(nameIdentifier);
        }
      }
    }
    final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();
    for (int i = 0; i < occurrenceMarkers.size(); i++) {
      RangeMarker marker = occurrenceMarkers.get(i);
      if (getExprMarker() != null && marker.getStartOffset() == getExprMarker().getStartOffset() && myExpr != null) {
        myOccurrences[i] = myExpr;
        continue;
      }
      final E psiExpression =
        restoreExpression(containingFile, psiField, marker, getLocalVariable() != null ? myLocalName : myExprText);
      if (psiExpression != null) {
        myOccurrences[i] = psiExpression;
      }
    }

    if (myExpr != null && myExpr.isPhysical()) {
      myExprMarker = createMarker(myExpr);
    }
    myOccurrenceMarkers = null;
    deleteTemplateField(psiField);
  }

  protected void deleteTemplateField(V psiField) {
    if (psiField.isValid()) {
      psiField.delete();
    }
  }

  @Override
  protected boolean performRefactoring() {
    if (!ensureValid()) return false;
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final String refactoringId = getRefactoringId();
      if (refactoringId != null) {
        final RefactoringEventData beforeData = new RefactoringEventData();
        final V localVariable = getLocalVariable();
        if (localVariable != null) {
          beforeData.addElement(localVariable);
        }
        else {
          final E beforeExpr = getBeforeExpr();
          if (beforeExpr != null) {
            beforeData.addElement(beforeExpr);
          }
        }
        myProject.getMessageBus()
          .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(refactoringId, beforeData);
      }
      performIntroduce();
    }, getCommandName(), getCommandName());

    V variable = getVariable();
    if (variable != null) {
      saveSettings(variable);
    }
    return false;
  }

  protected E getBeforeExpr() {
    return getExpr();
  }

  protected boolean ensureValid() {
    final String newName = getInputName();
    if (getLocalVariable() == null && myExpr == null ||
        newName == null ||
        getLocalVariable() != null && !getLocalVariable().isValid() ||
        myExpr != null && !myExpr.isValid()) {
      super.moveOffsetAfter(false);
      return false;
    }
    if (getLocalVariable() != null) {
      WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).withGroupId(getCommandName()).run(() -> getLocalVariable().setName(myLocalName));
    }

    if (!isIdentifier(newName, myExpr != null ? myExpr.getLanguage() : getLocalVariable().getLanguage())) return false;
    return true;
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    if (getLocalVariable() != null && getLocalVariable().isValid()) {
      myEditor.getCaretModel().moveToOffset(getLocalVariable().getTextOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
    else if (getExprMarker() != null) {
      final RangeMarker exprMarker = getExprMarker();
      if (exprMarker.isValid()) {
        myEditor.getCaretModel().moveToOffset(exprMarker.getStartOffset());
        myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    }
    super.moveOffsetAfter(success);
    if (myLocalMarker != null && !isRestart()) {
      myLocalMarker.dispose();
    }
    if (success) {
      performPostIntroduceTasks();
      final String refactoringId = getRefactoringId();
      if (refactoringId != null) {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(getVariable());
        myProject.getMessageBus()
          .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, afterData);
      }
    }
  }

  protected String getRefactoringId() {
    return null;
  }

  @Override
  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return super.startsOnTheSameElement(handler, element) || getLocalVariable() == element;
  }

  public V getLocalVariable() {
    if (myLocalVariable != null && myLocalVariable.isValid()) {
      return myLocalVariable;
    }
    if (myLocalMarker != null) {
      V variable = getVariable();
      PsiFile containingFile;
      if (variable != null) {
        containingFile = variable.getContainingFile();
      } else {
        containingFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
      }
      PsiNameIdentifierOwner identifierOwner = PsiTreeUtil.getParentOfType(containingFile.findElementAt(myLocalMarker.getStartOffset()),
                                                                           PsiNameIdentifierOwner.class, false);
      return identifierOwner != null && identifierOwner.getClass() == myLocalVariable.getClass() ? (V)identifierOwner : null;

    }
    return myLocalVariable;
  }

  @Override
  protected void showBalloon() {
    if (myFinished) return;
    super.showBalloon();
  }

  public boolean startsOnTheSameElement(E expr, V localVariable) {
    if (myExprMarker != null && myExprMarker.isValid() && expr != null && myExprMarker.getStartOffset() == expr.getTextOffset()) {
      return true;
    }

    if (myLocalMarker != null &&
        myLocalMarker.isValid() &&
        localVariable != null &&
        myLocalMarker.getStartOffset() == localVariable.getTextOffset()) {
      return true;
    }
    return isRestart();
  }

  @Nullable
  public static AbstractInplaceIntroducer getActiveIntroducer(@Nullable Editor editor) {
    if (editor == null) return null;
    return editor.getUserData(ACTIVE_INTRODUCE);
  }
}
