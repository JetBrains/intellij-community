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
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceVariable.VariableInplaceIntroducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 3/15/11
 */
public abstract class AbstractInplaceIntroducer extends VariableInplaceIntroducer {
  public AbstractInplaceIntroducer(Project project,
                                   TypeExpression expression,
                                   Editor editor,
                                   PsiVariable elementToRename,
                                   boolean cantChangeFinalModifier,
                                   boolean hasTypeSuggestion,
                                   RangeMarker exprMarker,
                                   List<RangeMarker> occurrenceMarkers, String commandName, String title) {
    super(project, expression, editor, elementToRename, cantChangeFinalModifier, hasTypeSuggestion, exprMarker, occurrenceMarkers, commandName, title);
  }

  protected abstract boolean isReplaceAllOccurrences();

  protected abstract PsiExpression getExpr();

  protected abstract PsiExpression[] getOccurrences();



  protected abstract List<RangeMarker> getOccurrenceMarkers();

  @Override
  protected void addReferenceAtCaret(Collection<PsiReference> refs) {
    final PsiVariable variable = getVariable();
    if (variable != null) {
      for (PsiReference reference : ReferencesSearch.search(variable)) {
        refs.remove(reference);
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
      for (PsiExpression expression : getOccurrences()) {
        stringUsages.add(Pair.<PsiElement, TextRange>create(expression, new TextRange(0, expression.getTextLength())));
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

  @Nullable
  protected static PsiExpression restoreExpression(PsiFile containingFile,
                                                   PsiVariable psiVariable,
                                                   PsiElementFactory elementFactory,
                                                   RangeMarker marker, String exprText) {
    if (exprText == null) return null;
    if (psiVariable == null || !psiVariable.isValid()) return null;
    final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
    final PsiExpression expression = PsiTreeUtil.getParentOfType(refVariableElement, PsiReferenceExpression.class);
    if (expression instanceof PsiReferenceExpression && (((PsiReferenceExpression)expression).resolve() == psiVariable ||
                                                         Comparing.strEqual(psiVariable.getName(), ((PsiReferenceExpression)expression).getReferenceName()))) {
      return (PsiExpression)expression.replace(elementFactory.createExpressionFromText(exprText, psiVariable));
    }
    return expression != null && expression.isValid() && expression.getText().equals(exprText) ? expression : null;
  }

  protected abstract class VisibilityListener implements ChangeListener {
    private Project myProject;
    private final String myCommandName;
    private Editor myEditor;

    protected VisibilityListener(Project project, String commandName, Editor editor) {
      myProject = project;
      myCommandName = commandName;
      myEditor = editor;
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      new WriteCommandAction(myProject, myCommandName, myCommandName) {
        @Override
        protected void run(Result result) throws Throwable {
          final Document document = myEditor.getDocument();
          PsiDocumentManager.getInstance(getProject()).commitDocument(document);
          final PsiVariable variable = getVariable();
          LOG.assertTrue(variable != null);
          final PsiModifierList modifierList = variable.getModifierList();
          LOG.assertTrue(modifierList != null);
          int textOffset = modifierList.getTextOffset();
          final String modifierListText = modifierList.getText();

          int length = PsiModifier.PUBLIC.length();
          int idx = modifierListText.indexOf(PsiModifier.PUBLIC);

          if (idx == -1) {
            idx = modifierListText.indexOf(PsiModifier.PROTECTED);
            length = PsiModifier.PROTECTED.length();
          }

          if (idx == -1) {
            idx = modifierListText.indexOf(PsiModifier.PRIVATE);
            length = PsiModifier.PRIVATE.length();
          }

          String visibility = getVisibility();
          if (visibility == PsiModifier.PACKAGE_LOCAL) {
            visibility = "";
          }

          final boolean wasPackageLocal = idx == -1;
          final boolean isPackageLocal = visibility.isEmpty();

          final int startOffset = textOffset + (wasPackageLocal ? 0 : idx);
          final int endOffset;
          if (wasPackageLocal) {
            endOffset = startOffset;
          }
          else {
            endOffset = textOffset + length + (isPackageLocal ? 1 : 0);
          }

          final String finalVisibility = visibility + (wasPackageLocal ? " " : "");

          Runnable runnable = new Runnable() {
            @Override
            public void run() {
              document.replaceString(startOffset, endOffset, finalVisibility);
            }
          };

          final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
          if (lookup != null) {
            lookup.performGuardedChange(runnable);
          } else {
            runnable.run();
          }
        }
      }.execute();
    }

    protected abstract String getVisibility();
  }
}
