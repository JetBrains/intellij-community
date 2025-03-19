// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.forloop;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiTypes;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public final class ReplaceForEachLoopWithOptimizedIndexedForLoopIntention extends ReplaceForEachLoopWithIndexedForLoopIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.optimized.indexed.for.loop.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.optimized.indexed.for.loop.intention.name");
  }

  @Override
  protected void createForLoopDeclaration(PsiForeachStatement statement,
                                          boolean isArray,
                                          String iteratedValueText,
                                          final String indexText,
                                          StringBuilder newStatement) {
    final String lengthText = isArray
                              ? createVariableName(iteratedValueText + "Length", PsiTypes.intType(), statement)
                              : createVariableName(iteratedValueText + "Size", PsiTypes.intType(), statement);

    newStatement.append("for(int ");
    newStatement.append(indexText);
    newStatement.append("=0,");
    newStatement.append(lengthText);
    newStatement.append('=');
    newStatement.append(iteratedValueText);
    newStatement.append(isArray ? ".length;" : ".size();");
    newStatement.append(indexText);
    newStatement.append('<');
    newStatement.append(lengthText);
    newStatement.append(';');
    newStatement.append(indexText);
    newStatement.append("++){");
  }
}
