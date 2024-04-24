// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public final class JavaSliceProvider implements SliceLanguageSupportProvider, SliceUsageTransformer {
  public static JavaSliceProvider getInstance() {
    return (JavaSliceProvider)LanguageSlicing.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
  }

  @Override
  public @NotNull SliceUsage createRootUsage(@NotNull PsiElement element, @NotNull SliceAnalysisParams params) {
    return JavaSliceUsage.createRootUsage(element, params);
  }

  @Override
  public @Nullable Collection<SliceUsage> transform(@NotNull SliceUsage usage) {
    if (usage instanceof JavaSliceUsage) return null;

    PsiElement element = usage.getElement();
    SliceUsage parent = usage.getParent();

    if (usage.params.dataFlowToThis && element instanceof PsiMethod) {
      return SliceUtil.collectMethodReturnValues(
        parent,
        parent instanceof JavaSliceUsage ? ((JavaSliceUsage)parent).getSubstitutor() : PsiSubstitutor.EMPTY,
        (PsiMethod) element
      );
    }

    if (!(element instanceof PsiExpression || element instanceof PsiVariable)) return null;
    SliceUsage newUsage = parent != null
                        ? new JavaSliceUsage(element, parent, PsiSubstitutor.EMPTY)
                        : createRootUsage(element, usage.params);
    return Collections.singletonList(newUsage);
  }

  @Override
  public @Nullable PsiElement getExpressionAtCaret(@NotNull PsiElement atCaret, boolean dataFlowToThis) {
    PsiElement element = PsiTreeUtil.getParentOfType(atCaret, PsiExpression.class, PsiVariable.class, PsiMethod.class);
    if (dataFlowToThis && element instanceof PsiLiteralExpression) return null;
    return element;
  }

  @Override
  public @NotNull PsiElement getElementForDescription(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      PsiElement elementToSlice = ((PsiReferenceExpression)element).resolve();
      if (elementToSlice != null) {
        return elementToSlice;
      }
    }
    return element;
  }

  @Override
  public @NotNull SliceUsageCellRendererBase getRenderer() {
    return new JavaSliceUsageCellRenderer();
  }

  @Override
  public void startAnalyzeLeafValues(@NotNull AbstractTreeStructure structure, @NotNull Runnable finalRunnable) {
    JavaSlicerAnalysisUtil.createLeafAnalyzer().startAnalyzeValues(structure, finalRunnable);
  }

  @Override
  public void startAnalyzeNullness(@NotNull AbstractTreeStructure structure, @NotNull Runnable finalRunnable) {
    new JavaSliceNullnessAnalyzer().startAnalyzeNullness(structure, finalRunnable);
  }

  @Override
  public void registerExtraPanelActions(@NotNull DefaultActionGroup actionGroup, @NotNull SliceTreeBuilder sliceTreeBuilder) {
    if (sliceTreeBuilder.dataFlowToThis) {
      actionGroup.add(new GroupByLeavesAction(sliceTreeBuilder));
      actionGroup.add(new CanItBeNullAction(sliceTreeBuilder));
    }
  }

  @Override
  public boolean supportValueFilters(@NotNull PsiElement expression) {
    PsiType type = getType(expression);
    return type != null && !PsiTypes.voidType().equals(type) && !PsiTypes.nullType().equals(type);
  }

  @Override
  public @NotNull SliceValueFilter parseFilter(@NotNull PsiElement expression, @NotNull String filter) throws SliceFilterParseException {
    PsiType type = getType(expression);
    if (type == null) {
      return SliceLanguageSupportProvider.super.parseFilter(expression, filter);
    }
    if (filter.equals("null")) {
      if (type instanceof PsiPrimitiveType) {
        throw new SliceFilterParseException(
          JavaBundle.message("slice.filter.parse.error.null.filter.not.applicable.for.primitive.type", type.getPresentableText()));
      }
      return new JavaValueFilter(DfTypes.NULL);
    }
    if (filter.equals("!null")) {
      if (type instanceof PsiPrimitiveType) {
        throw new SliceFilterParseException(
          JavaBundle.message("slice.filter.parse.error.not.null.filter.not.applicable.for.primitive.type", type.getPresentableText()));
      }
      return new JavaValueFilter(DfTypes.NOT_NULL_OBJECT);
    }
    RelationType relationType = RelationType.EQ;
    if (PsiTypes.byteType().equals(type) ||
        PsiTypes.charType().equals(type) ||
        PsiTypes.shortType().equals(type) ||
        PsiTypes.intType().equals(type) ||
        PsiTypes.longType().equals(type)) {
      for (RelationType relType : RelationType.values()) {
        if (filter.startsWith(relType.toString())) {
          relationType = relType;
          filter = filter.substring(relType.toString().length()).trim();
          break;
        }
      }
    }
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (psiClass != null && psiClass.isEnum()) {
      PsiField enumConstant = psiClass.findFieldByName(filter, false);
      if (enumConstant instanceof PsiEnumConstant) {
        return new JavaValueFilter(DfTypes.referenceConstant(enumConstant, type));
      } else {
        throw new SliceFilterParseException(JavaBundle.message("slice.filter.parse.error.enum.constant.not.found", filter));
      }
    }
    PsiExpression constant;
    try {
      constant = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(filter, expression);
    }
    catch (IncorrectOperationException ignore) {
      throw new SliceFilterParseException(JavaBundle.message("slice.filter.parse.error.incorrect.expression", filter));
    }
    PsiType constantType = constant.getType();
    if (constantType == null || !type.isAssignableFrom(constantType)) {
      throw new SliceFilterParseException(JavaBundle.message("slice.filter.parse.error.incorrect.constant.type", type.getPresentableText()));
    }
    Object o = ExpressionUtils.computeConstantExpression(constant);
    if (o == null) {
      throw new SliceFilterParseException(JavaBundle.message("slice.filter.parse.error.expression.must.evaluate.to.constant", filter));
    }
    if (relationType != RelationType.EQ) {
      if (!(o instanceof Number)) {
        throw new SliceFilterParseException(JavaBundle.message("slice.filter.parse.error.incorrect.constant.expected.number", filter));
      }
      if (PsiTypes.longType().equals(type)) {
        LongRangeSet rangeSet = LongRangeSet.point(((Number)o).longValue()).fromRelation(relationType);
        return new JavaValueFilter(DfTypes.longRange(rangeSet));
      }
      LongRangeSet rangeSet = LongRangeSet.point(((Number)o).intValue()).fromRelation(relationType);
      return new JavaValueFilter(DfTypes.intRangeClamped(rangeSet));
    }
    return new JavaValueFilter(DfTypes.constant(o, type));
  }

  private static @Nullable PsiType getType(@NotNull PsiElement expression) {
    if (expression instanceof PsiExpression) {
      return ((PsiExpression)expression).getType();
    }
    else if (expression instanceof PsiVariable) {
      return ((PsiVariable)expression).getType();
    }
    else if (expression instanceof PsiMethod) {
      return ((PsiMethod)expression).getReturnType();
    }
    return null;
  }
}
