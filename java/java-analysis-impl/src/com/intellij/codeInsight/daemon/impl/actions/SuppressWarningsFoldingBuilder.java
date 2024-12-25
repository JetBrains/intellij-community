// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SuppressWarningsFoldingBuilder extends FoldingBuilderEx {
  private static final Logger LOG = Logger.getInstance(SuppressWarningsFoldingBuilder.class);
  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    if (!(root instanceof PsiJavaFile) || quick || !JavaCodeFoldingSettings.getInstance().isCollapseSuppressWarnings()) {
      return FoldingDescriptor.EMPTY_ARRAY;
    }
    if (!PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, root)) {
      return FoldingDescriptor.EMPTY_ARRAY;
    }
    final List<FoldingDescriptor> result = new ArrayList<>();
    root.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override
      public void visitAnnotation(@NotNull PsiAnnotation annotation) {
        if (Comparing.strEqual(annotation.getQualifiedName(), SuppressWarnings.class.getName())) {
          result.add(new FoldingDescriptor(annotation.getNode(), annotation.getTextRange(), null, placeholderText(annotation),
                                           JavaCodeFoldingSettings.getInstance().isCollapseSuppressWarnings(), Collections.emptySet()));
        }
        super.visitAnnotation(annotation);
      }
    });
    return result.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    LOG.error("unknown element " + node);
    return null;
  }

  private static @NotNull String placeholderText(@NotNull PsiAnnotation element) {
    return "/" + StringUtil.join(element.getParameterList().getAttributes(), value -> getMemberValueText(value.getValue()), ", ") + "/";
  }

  private static @NotNull String getMemberValueText(@Nullable PsiAnnotationMemberValue _memberValue) {
    return StringUtil.join(AnnotationUtil.arrayAttributeValues(_memberValue), memberValue -> {
      if (memberValue instanceof PsiLiteral) {
        final Object o = ((PsiLiteral)memberValue).getValue();
        if (o != null) {
          return o.toString();
        }
      }
      return memberValue != null ? memberValue.getText() : "";
    }, ", ");
  }


  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return JavaCodeFoldingSettings.getInstance().isCollapseSuppressWarnings();
  }
}
