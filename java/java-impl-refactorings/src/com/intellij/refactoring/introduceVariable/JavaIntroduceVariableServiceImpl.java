// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase.IntroduceVariableResult;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class JavaIntroduceVariableServiceImpl extends JavaIntroduceVariableService {
  @Override
  public @NotNull List<@NotNull TextRange> getOccurrences(@NotNull PsiExpression expression) {
    var result = IntroduceVariableBase.getIntroduceVariableContext(expression.getProject(), expression, null);
    if (!(result instanceof IntroduceVariableResult.Context ctx)) return List.of(expression.getTextRange());
    return Arrays.stream(ctx.occurrenceManager().getOccurrences()).map(PsiExpression::getTextRange).toList();
  }

  @Override
  public @Nullable PsiVariable introduceVariable(@NotNull PsiExpression expression, boolean replaceAll) {
    Project project = expression.getProject();

    var result = IntroduceVariableBase.getIntroduceVariableContext(project, expression, null);
    if (!(result instanceof IntroduceVariableResult.Context ctx)) return null;

    PsiType type = ctx.originalType();
    PsiExpression[] occurrences = replaceAll
                                  ? ctx.occurrenceManager().getOccurrences()
                                  : new PsiExpression[]{ctx.expression()};

    PsiElement chosenAnchor = replaceAll
                              ? IntroduceVariableBase.getAnchor(occurrences)
                              : ctx.anchorStatement();
    if (chosenAnchor == null) chosenAnchor = ctx.anchorStatement();

    SuggestedNameInfo suggestedName = CommonJavaRefactoringUtil.getSuggestedName(type, ctx.expression(), chosenAnchor);
    String name = suggestedName.names.length > 0 ? suggestedName.names[0] : "v";
    boolean declareFinal = IntroduceVariableBase.createFinals(expression.getContainingFile());
    boolean declareVarType = IntroduceVariableBase.createVarType() &&
                             IntroduceVariableBase.canBeExtractedWithoutExplicitType(expression);

    PsiElement finalChosenAnchor = chosenAnchor;
    IntroduceVariableSettings settings = new IntroduceVariableSettings() {
      @Override
      public String getEnteredName() { return name; }

      @Override
      public boolean isReplaceAllOccurrences() { return replaceAll; }

      @Override
      public boolean isDeclareFinal() { return declareFinal; }

      @Override
      public boolean isDeclareVarType() { return declareVarType; }

      @Override
      public boolean isReplaceLValues() { return false; }

      @Override
      public PsiType getSelectedType() { return type; }

      @Override
      public boolean isOK() { return true; }
    };
    PsiExpression toChange = ctx.expression();
    if (expression.getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL) == Boolean.TRUE) {
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(finalChosenAnchor, true);
      Arrays.stream(occurrences).forEach(e -> ElementToWorkOn.REPLACE_NON_PHYSICAL.set(e, true));
    }
    return VariableExtractor.introduceInReadAction(project, toChange, finalChosenAnchor, occurrences, settings);
  }
}
