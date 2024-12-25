// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class OptionalContainsCollectionInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message(
      type instanceof PsiArrayType ? "optional.contains.array.problem.descriptor" : "optional.contains.collection.problem.descriptor");
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.STREAM_OPTIONAL);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OptionalContainsCollectionVisitor();
  }

  private static class OptionalContainsCollectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
      super.visitTypeElement(typeElement);
      final PsiType type = typeElement.getType();
      if (!TypeUtils.isOptional(type)) {
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
      if (referenceElement == null) {
        return;
      }
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList == null) {
        return;
      }
      final PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
      if (typeParameterElements.length != 1) {
        return;
      }
      final PsiTypeElement typeParameterElement = typeParameterElements[0];
      final PsiType parameterType = typeParameterElement.getType();
      if (!(parameterType instanceof PsiArrayType) && !CollectionUtils.isCollectionClassOrInterface(parameterType)) {
        return;
      }
      registerError(typeParameterElement, parameterType);
    }
  }
}
