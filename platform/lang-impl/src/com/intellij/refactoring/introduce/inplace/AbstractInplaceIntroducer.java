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
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
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
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 3/15/11
 */
public abstract class AbstractInplaceIntroducer<V extends PsiNameIdentifierOwner, E extends PsiElement> extends AbstractInplaceVariableIntroducer<E> {
  protected final V myLocalVariable;
  protected RangeMarker myLocalMarker;

  private final String myExprText;
  private final String myLocalName;

  protected String myConstantName;
  public static final Key<AbstractInplaceIntroducer> ACTIVE_INTRODUCE = Key.create("ACTIVE_INTRODUCE");

  protected SimpleColoredComponent myLabel = new SimpleColoredComponent();
  private DocumentAdapter myDocumentAdapter;
  protected final JPanel myWholePanel;

  public AbstractInplaceIntroducer(Project project,
                                   Editor editor,
                                   E expr,
                                   V localVariable,
                                   E[] occurrences,
                                   String title) {
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

    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 0));
    myLabel.append("#########################");

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(null);

    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    final Shortcut[] shortcuts = keymap.getShortcuts(getActionName());
    if (shortcuts.length > 0) {
      setAdvertisementText("Press " + shortcuts[0] + " to show dialog");
    }
  }

  protected abstract String getActionName();
  protected abstract String getCommandName();

  protected abstract V createFieldToStartTemplateOn(boolean replaceAll, String[] names);
  protected abstract String[] suggestNames(boolean replaceAll, V variable);

  protected abstract void performIntroduce();
  protected void performPostIntroduceTasks() {}

  public abstract boolean isReplaceAllOccurrences();
  protected abstract JComponent getComponent();

  protected abstract void saveSettings(V variable);
  protected abstract V getVariable();

  public abstract E restoreExpression(PsiFile containingFile, V variable, RangeMarker marker, String exprText);
  protected void restoreAnchors() {}

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
          myDocumentAdapter = new DocumentAdapter() {
            @Override
            public void documentChanged(DocumentEvent e) {
              final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
              if (templateState != null) {
                final String variableValue =
                  templateState.getVariableValue(VariableInplaceRenamer.PRIMARY_VARIABLE_NAME).getText();
                updateTitle(getVariable(), variableValue);
              }
            }
          };
          myEditor.getDocument().addDocumentListener(myDocumentAdapter);
          updateTitle(getVariable());
          if (TemplateManagerImpl.getTemplateState(myEditor) != null) {
            myEditor.putUserData(ACTIVE_INTRODUCE, AbstractInplaceIntroducer.this);
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

  protected void updateTitle(V variable, String value) {
    myLabel.clear();
    myLabel.append(variable.getText().replace(variable.getName(), value), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    revalidate();
  }

  protected void updateTitle(V variable) {
    myLabel.clear();
    myLabel.append(variable.getText(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
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
    myEditor.getDocument().removeDocumentListener(myDocumentAdapter);
    super.finish();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final V variable = getVariable();
    if (variable == null) {
      return;
    }
    restoreState(variable);
  }

  @Override
  protected void addReferenceAtCaret(Collection<PsiReference> refs) {
    //super.addReferenceAtCaret(refs);
    final V variable = getLocalVariable();
    if (variable != null) {
      for (PsiReference reference : ReferencesSearch.search(variable)) {
        refs.add(reference);
      }
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

      final V localVariable = getLocalVariable();
      if (localVariable != null) {
        final PsiElement nameIdentifier = localVariable.getNameIdentifier();
        if (nameIdentifier != null) {
          int length = nameIdentifier.getTextLength();
          stringUsages.add(Pair.<PsiElement, TextRange>create(nameIdentifier, new TextRange(0, length)));
        }
      }
    }
    else if (getExpr() != null) {
      stringUsages.add(Pair.<PsiElement, TextRange>create(getExpr(), new TextRange(0, getExpr().getTextLength())));
    }
  }

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
    final int variableNameLength = getVariable().getName().length();
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
    super.addHighlights(ranges, editor, highlighters, highlightManager);
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

        restoreAnchors();
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
      saveSettings(getVariable());
    }
    if (getLocalVariable() != null && getLocalVariable().isValid()) {
      myEditor.getCaretModel().moveToOffset(getLocalVariable().getTextOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
    else if (getExprMarker() != null) {
      myEditor.getCaretModel().moveToOffset(getExprMarker().getStartOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
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
      return identifierOwner.getClass() == myLocalVariable.getClass() ? (V)identifierOwner : null;

    }
    return myLocalVariable;
  }

  public static void stopIntroduce(Editor editor) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null) {
      templateState.gotoEnd(true);
    }
  }

  @Nullable
  public static AbstractInplaceIntroducer getActiveIntroducer(Editor editor) {
    return editor.getUserData(ACTIVE_INTRODUCE);
  }
}
