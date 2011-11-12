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
package com.intellij.refactoring.introduce.inplace;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.DottedBorder;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 3/15/11
 */
public abstract class AbstractInplaceIntroducer<V extends PsiNameIdentifierOwner, E extends PsiElement> extends
                                                                                                        InplaceVariableIntroducer<E> {
  protected final V myLocalVariable;
  protected RangeMarker myLocalMarker;

  protected final String myExprText;
  private final String myLocalName;

  protected String myConstantName;
  public static final Key<AbstractInplaceIntroducer> ACTIVE_INTRODUCE = Key.create("ACTIVE_INTRODUCE");

  private EditorEx myPreview;
  private final JComponent myPreviewComponent;

  private DocumentAdapter myDocumentAdapter;
  protected final JPanel myWholePanel;

  public AbstractInplaceIntroducer(Project project,
                                   Editor editor,
                                   E expr,
                                   @Nullable V localVariable,
                                   E[] occurrences,
                                   String title,
                                   final FileType languageFileType) {
    super(null, editor, project, title, occurrences, expr);
    myLocalVariable = localVariable;
    if (localVariable != null) {
      final PsiElement nameIdentifier = localVariable.getNameIdentifier();
      if (nameIdentifier != null) {
        myLocalMarker = editor.getDocument().createRangeMarker(nameIdentifier.getTextRange());
      }
    }
    else {
      myLocalMarker = null;
    }
    myExprText = expr != null ? expr.getText() : null;
    myLocalName = localVariable != null ? localVariable.getName() : null;

    myPreview =
      (EditorEx)EditorFactory.getInstance().createEditor(EditorFactory.getInstance().createDocument(""), project, languageFileType, true);
    myPreview.setOneLineMode(true);
    final EditorSettings settings = myPreview.getSettings();
    settings.setAdditionalLinesCount(0);
    settings.setAdditionalColumnsCount(1);
    settings.setRightMarginShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setLineNumbersShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setVirtualSpace(false);
    myPreview.setHorizontalScrollbarVisible(false);
    myPreview.setVerticalScrollbarVisible(false);
    myPreview.setCaretEnabled(false);
    settings.setLineCursorWidth(1);

    final Color bg = myPreview.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
    myPreview.setBackgroundColor(bg);
    myPreview.setBorder(BorderFactory.createCompoundBorder(new DottedBorder(Color.gray), new LineBorder(bg, 2)));

    myPreviewComponent = new JPanel(new BorderLayout());
    myPreviewComponent.add(myPreview.getComponent(), BorderLayout.CENTER);
    myPreviewComponent.setBorder(new EmptyBorder(2, 2, 6, 2));

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(null);

    showDialogAdvertisement(getActionName());
  }

  protected final void setPreviewText(final String text) {
    if (myPreview == null) return; //already disposed
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myPreview.getDocument().replaceString(0, myPreview.getDocument().getTextLength(), text);
      }
    });
  }

  protected final JComponent getPreviewComponent() {
    return myPreviewComponent;
  }

  protected final Editor getPreviewEditor() {
    return myPreview;
  }

  /**
   * Returns ID of the action the shortcut of which is used to show the non-in-place refactoring dialog.
   *
   * @return action ID
   */
  protected abstract String getActionName();

  /**
   * Returns the name of the command performed by the refactoring.
   *
   * @return command name
   */
  protected abstract String getCommandName();

  /**
   * Creates an initial version of the declaration for the introduced element. Note that this method is not called in a write action
   * and most likely needs to create one itself.
   *
   * @param replaceAll whether all occurrences are going to be replaced
   * @param names      the suggested names for the declaration
   * @return the declaration
   */
  @Nullable
  protected abstract V createFieldToStartTemplateOn(boolean replaceAll, String[] names);

  /**
   * Returns the suggested names for the introduced element.
   *
   * @param replaceAll whether all occurrences are going to be replaced
   * @param variable   introduced element declaration, if already created.
   * @return the suggested names
   */
  protected abstract String[] suggestNames(boolean replaceAll, @Nullable V variable);

  protected abstract void performIntroduce();
  protected void performPostIntroduceTasks() {}

  public abstract boolean isReplaceAllOccurrences();
  public abstract void setReplaceAllOccurrences(boolean allOccurrences);
  protected abstract JComponent getComponent();

  protected abstract void saveSettings(@NotNull V variable);
  protected abstract V getVariable();

  public abstract E restoreExpression(PsiFile containingFile, V variable, RangeMarker marker, String exprText);

  /**
   * Begins the in-place refactoring operation.
   *
   * @return true if the in-place refactoring was successfully started, false if it failed to start and a dialog should be shown instead.
   */
  public boolean startInplaceIntroduceTemplate() {
    final boolean replaceAllOccurrences = isReplaceAllOccurrences();
    final Ref<Boolean> result = new Ref<Boolean>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        final String[] names = suggestNames(replaceAllOccurrences, getLocalVariable());
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
        final V variable = createFieldToStartTemplateOn(replaceAllOccurrences, names);
        boolean started = false;
        if (variable != null) {
          myEditor.getCaretModel().moveToOffset(r.getStartOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

          final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<String>();
          nameSuggestions.add(variable.getName());
          nameSuggestions.addAll(Arrays.asList(names));
          initOccurrencesMarkers();
          setElementToRename(variable);
          started = AbstractInplaceIntroducer.super.performInplaceRename(false, nameSuggestions);
          if (started) {
            myDocumentAdapter = new DocumentAdapter() {
              @Override
              public void documentChanged(DocumentEvent e) {
                final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
                if (templateState != null) {
                  final TextResult value = templateState.getVariableValue(VariableInplaceRenamer.PRIMARY_VARIABLE_NAME);
                  if (value != null) {
                    updateTitle(getVariable(), value.getText());
                  }
                }
              }
            };
            myEditor.getDocument().addDocumentListener(myDocumentAdapter);
            updateTitle(getVariable());
            if (TemplateManagerImpl.getTemplateState(myEditor) != null) {
              myEditor.putUserData(ACTIVE_INTRODUCE, AbstractInplaceIntroducer.this);
            }
          }
        }
        result.set(started);
        if (!started && variable != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              variable.delete();
            }
          });
        }
      }

    }, getCommandName(), getCommandName());
    return result.get();
  }

  protected void updateTitle(@Nullable V variable, String value) {
    if (variable == null) return;

    setPreviewText(variable.getText().replace(variable.getName(), value));
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
      ((BalloonImpl)myBalloon).revalidate(new PositionTracker.Static<Balloon>(myTarget));
    }
  }

  public void restartInplaceIntroduceTemplate() {
    Runnable restartTemplateRunnable = new Runnable() {
      public void run() {
        final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
        if (templateState != null) {
          myEditor.putUserData(INTRODUCE_RESTART, true);
          try {
            templateState.gotoEnd(true);
            startInplaceIntroduceTemplate();
          }
          finally {
            myEditor.putUserData(INTRODUCE_RESTART, false);
          }
        }
        updateTitle(getVariable());
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, restartTemplateRunnable, getCommandName(), getCommandName());
  }

  public String getInputName() {
    return myConstantName;
  }

  @Override
  protected boolean performAutomaticRename() {
    return false;
  }


  @Override
  public void finish() {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
    if (templateState != null) {
      myEditor.putUserData(ACTIVE_INTRODUCE, null);
    }
    if (myDocumentAdapter != null) {
      myEditor.getDocument().removeDocumentListener(myDocumentAdapter);
    }
    if (myBalloon == null) {
      releaseIfNotRestart();
    }
    super.finish();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final V variable = getVariable();
    if (variable == null) {
      return;
    }
    restoreState(variable);
  }

  @Override
  protected void releaseResources() {
    if (myPreview == null) return;

    EditorFactory.getInstance().releaseEditor(myPreview);
    myPreview = null;
  }

  @Override
  protected void addReferenceAtCaret(Collection<PsiReference> refs) {
    final V variable = getLocalVariable();
    if (variable != null) {
      for (PsiReference reference : ReferencesSearch.search(variable)) {
        refs.add(reference);
      }
    } else {
      refs.clear();
    }
  }

  @Override
  protected boolean appendAdditionalElement(List<Pair<PsiElement, TextRange>> stringUsages) {
    return true;
  }

  @Override
  protected void collectAdditionalElementsToRename(boolean processTextOccurrences, List<Pair<PsiElement, TextRange>> stringUsages) {
    if (isReplaceAllOccurrences()) {
      for (E expression : getOccurrences()) {
        stringUsages.add(Pair.<PsiElement, TextRange>create(expression, new TextRange(0, expression.getTextLength())));
      }
    }  else if (getExpr() != null) {
      correctExpression();
      stringUsages.add(Pair.<PsiElement, TextRange>create(getExpr(), new TextRange(0, getExpr().getTextLength())));
    }

    final V localVariable = getLocalVariable();
    if (localVariable != null) {
      final PsiElement nameIdentifier = localVariable.getNameIdentifier();
      if (nameIdentifier != null) {
        int length = nameIdentifier.getTextLength();
        stringUsages.add(Pair.<PsiElement, TextRange>create(nameIdentifier, new TextRange(0, length)));
      }
    }
  }

  protected void correctExpression() {}

  @Override
  protected void collectAdditionalRangesToHighlight(Map<TextRange, TextAttributes> rangesToHighlight,
                                                    Collection<Pair<PsiElement, TextRange>> stringUsages,
                                                    EditorColorsManager colorsManager) {
  }

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
          final int startOffset = marker.getStartOffset();
          highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + variableNameLength, attributes, 0, highlighters, null);
        }
      }
      else if (getExpr() != null) {
        final int startOffset = getExprMarker().getStartOffset();
        highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + variableNameLength, attributes, 0, highlighters, null);
      }
    }
  }

  protected void restoreState(final V psiField) {
    myConstantName = psiField.getName();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final PsiFile containingFile = psiField.getContainingFile();
        final RangeMarker exprMarker = getExprMarker();
        if (exprMarker != null) {
          myExpr = restoreExpression(containingFile, psiField, exprMarker, myExprText);
          if (myExpr != null && myExpr.isPhysical()) {
            myExprMarker = myEditor.getDocument().createRangeMarker(myExpr.getTextRange());
          }
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
            final PsiElement nameIdentifier = localVariable.getNameIdentifier();
            if (nameIdentifier != null) {
              myLocalMarker = myEditor.getDocument().createRangeMarker(nameIdentifier.getTextRange());
            }
          }
        }
        final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();
        for (int i = 0, occurrenceMarkersSize = occurrenceMarkers.size(); i < occurrenceMarkersSize; i++) {
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

        myOccurrenceMarkers = null;
        if (psiField.isValid()) {
          psiField.delete();
        }
      }
    });
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    if (success) {
      if (getLocalVariable() == null && myExpr == null ||
          getInputName() == null ||
          getLocalVariable() != null && !getLocalVariable().isValid() ||
          myExpr != null && !myExpr.isValid()) {
        super.moveOffsetAfter(false);
        return;
      }
      if (getLocalVariable() != null) {
        new WriteCommandAction(myProject, getCommandName(), getCommandName()) {
          @Override
          protected void run(Result result) throws Throwable {
            getLocalVariable().setName(myLocalName);
          }
        }.execute();
      }
      performIntroduce();
      V variable = getVariable();
      if (variable != null) {
        saveSettings(variable);
      }
    }
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
    if (success) {
      performPostIntroduceTasks();
    }
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

  public void stopIntroduce(Editor editor) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null) {
      final Runnable runnable = new Runnable() {
        public void run() {
          templateState.gotoEnd(true);
        }
      };
      CommandProcessor.getInstance().executeCommand(myProject, runnable, getCommandName(), getCommandName());
    }
  }

  @Nullable
  public static AbstractInplaceIntroducer getActiveIntroducer(@Nullable Editor editor) {
    if (editor == null) return null;
    return editor.getUserData(ACTIVE_INTRODUCE);
  }
}
