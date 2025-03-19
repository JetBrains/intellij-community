// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
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
                                    @NlsContexts.Command String title) {
    super(project, settings, chosenAnchor, editor, expr, true, occurrences, selectorManager, title);
  }

  @Override
  protected @Nullable PsiVariable getVariable() {
    if (myParameter != null && myParameter.isValid()) {
      return myParameter;
    }
    return super.getVariable();
  }

  @Override
  protected @Nullable PsiElement checkLocalScope() {
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
