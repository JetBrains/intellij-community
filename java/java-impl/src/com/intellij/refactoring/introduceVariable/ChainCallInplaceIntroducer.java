/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceVariable;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.chainCall.ChainCallExtractor;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.ArrayUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ChainCallInplaceIntroducer extends JavaVariableInplaceIntroducer {
  private PsiParameter myParameter;
  private PsiElement myBlock;

  public ChainCallInplaceIntroducer(Project project,
                                    IntroduceVariableSettings settings,
                                    PsiElement chosenAnchor,
                                    Editor editor,
                                    PsiExpression expr,
                                    PsiExpression[] occurrences,
                                    TypeSelectorManagerImpl selectorManager,
                                    String title) {
    super(project, settings, chosenAnchor, editor, expr, true, occurrences, selectorManager, title);
  }

  @Nullable
  @Override
  protected PsiVariable getVariable() {
    if (myParameter != null && myParameter.isValid()) {
      return myParameter;
    }
    return super.getVariable();
  }

  @Nullable
  @Override
  protected PsiElement checkLocalScope() {
    return myBlock;
  }

  @Override
  protected PsiVariable createFieldToStartTemplateOn(String[] names, PsiType psiType) {
    PsiVariable variable = introduceVariable();
    if (variable instanceof PsiLocalVariable) {
      PsiLambdaExpression lambda = WriteAction
        .compute(() -> ChainCallExtractor.extractMappingStep(myProject, (PsiLocalVariable)variable));
      if (lambda != null) {
        PsiParameter parameter = Objects.requireNonNull(ArrayUtil.getFirstElement(lambda.getParameterList().getParameters()));
        myParameter = parameter;
        myBlock = PsiTreeUtil.getParentOfType(lambda, PsiCodeBlock.class, PsiLambdaExpression.class, PsiMember.class);
        myExprMarker = null;
        myExpr = null;
        myOccurrences = StreamEx.of(ReferencesSearch.search(parameter).findAll()).map(PsiReference::getElement).select(PsiExpression.class)
          .toArray(PsiExpression[]::new);
        myOccurrenceMarkers = null;
        final PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier != null) {
          myEditor.getCaretModel().moveToOffset(identifier.getTextOffset());
        }
        setAdvertisementText(null);
        PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
        initOccurrencesMarkers();
        return parameter;
      }
      else if (!variable.isValid()) {
        return null;
      }
    }
    return variable;
  }
}
