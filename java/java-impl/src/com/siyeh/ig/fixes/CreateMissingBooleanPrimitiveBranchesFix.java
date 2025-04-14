// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Function;

public final class CreateMissingBooleanPrimitiveBranchesFix extends CreateMissingSwitchBranchesFix {
  private static final List<String> ALL_BOOLEAN_BRANCHES = List.of(JavaKeywords.TRUE, JavaKeywords.FALSE);

  private CreateMissingBooleanPrimitiveBranchesFix(@NotNull PsiSwitchBlock block, @NotNull Set<String> names) {
    super(block, names);
  }

  public static @Nullable CreateMissingBooleanPrimitiveBranchesFix createFix(@NotNull PsiSwitchBlock block) {
    PsiExpression selectorExpression = block.getExpression();
    if (selectorExpression == null) return null;
    PsiType selectorExpressionType = selectorExpression.getType();
    if (selectorExpressionType == null) return null;
    PsiPrimitiveType selectorPrimitiveType = PsiPrimitiveType.getOptionallyUnboxedType(selectorExpressionType);
    if (selectorPrimitiveType == null) return null;
    if (!PsiTypes.booleanType().equals(selectorPrimitiveType)) return null;
    if (JavaPsiSwitchUtil.findDefaultElement(block) != null) return null;
    List<PsiElement> branches = JavaPsiSwitchUtil.getSwitchBranches(block);
    PsiClassType boxedBooleanType = selectorPrimitiveType.getBoxedType(block);
    if (boxedBooleanType == null) return null;
    Set<String> existed = new HashSet<>();
    for (PsiElement branch : branches) {
      if (branch instanceof PsiTypeTestPattern testPattern) {
        PsiPatternVariable patternVariable = testPattern.getPatternVariable();
        if (patternVariable == null) continue;
        PsiType type = patternVariable.getType();
        if (type.isAssignableFrom(boxedBooleanType)) return null;
      }
      if (branch instanceof PsiExpression expression && ExpressionUtils.computeConstantExpression(expression) instanceof Boolean booleanValue) {
        existed.add(booleanValue.toString());
      }
    }
    List<String> missed = new ArrayList<>();
    for (String branch : ALL_BOOLEAN_BRANCHES) {
      if (!existed.contains(branch)) {
        missed.add(branch);
      }
    }
    if (missed.isEmpty()) return null;
    return new CreateMissingBooleanPrimitiveBranchesFix(block, new LinkedHashSet<>(missed));
  }

  public static @Nullable PsiBasedModCommandAction<PsiSwitchBlock> createWithNull(@NotNull PsiSwitchBlock block) {
    return createWithNull(block, () -> createFix(block));
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.missing.boolean.switch.branches.fix.family.name");
  }

  @Override
  protected @NotNull List<String> getAllNames(@NotNull PsiClass aClass, @NotNull PsiSwitchBlock switchBlock) {
    return ALL_BOOLEAN_BRANCHES;
  }

  @Override
  protected @NotNull Function<PsiSwitchLabelStatementBase, @Unmodifiable List<String>> getCaseExtractor() {
    return label -> {
      PsiCaseLabelElementList list = label.getCaseLabelElementList();
      if (list == null) return Collections.emptyList();
      return ContainerUtil.map(list.getElements(), PsiCaseLabelElement::getText);
    };
  }
}
