// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.tree.IElementType;

import java.util.List;

public class StatementGroup {
  public final IElementType type;
  public final List<PsiStatement> statements;

  private StatementGroup(IElementType type, List<PsiStatement> statements) {
    this.type = type;
    this.statements = statements;
  }

  public static StatementGroup of(List<PsiStatement> statements) {
    final IElementType type = findTypeAndCheck(statements);
    return new StatementGroup(type, statements);
  }

  private static IElementType findTypeAndCheck(List<PsiStatement> statements) {
    if (statements.isEmpty()) throw new IllegalArgumentException("Statement group is empty");
    final IElementType type = statements.get(0).getNode().getElementType();
    final boolean areOfSameType =
      statements.stream().map(statement -> statement.getNode().getElementType()).allMatch(currentType -> currentType == type);
    if (! areOfSameType) throw new IllegalArgumentException("Some statements are of different type");
    return type;
  }

  public boolean areEffectivelySame(){
    final PsiStatement firstStatement = statements.get(0);
    return statements.stream().allMatch(statement -> PsiEquivalenceUtil.areElementsEquivalent(firstStatement, statement));
  }
}
