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

package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SuppressWarningsFoldingBuilder extends FoldingBuilderEx {
  private static final Logger LOG = Logger.getInstance(SuppressWarningsFoldingBuilder.class);
  @NotNull
  @Override
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    if (!(root instanceof PsiJavaFile) || quick || !JavaCodeFoldingSettings.getInstance().isCollapseSuppressWarnings()) {
      return FoldingDescriptor.EMPTY;
    }
    if (!PsiUtil.isLanguageLevel5OrHigher(root)) {
      return FoldingDescriptor.EMPTY;
    }
    final List<FoldingDescriptor> result = new ArrayList<>();
    root.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        if (Comparing.strEqual(annotation.getQualifiedName(), SuppressWarnings.class.getName())) {
          result.add(new NamedFoldingDescriptor(annotation.getNode(), annotation.getTextRange(), null, placeholderText(annotation), JavaCodeFoldingSettings.getInstance().isCollapseSuppressWarnings(), Collections
            .emptySet()));
        }
        super.visitAnnotation(annotation);
      }
    });
    return result.toArray(FoldingDescriptor.EMPTY);
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    LOG.error("unknown element " + node);
    return null;
  }

  @NotNull
  private static String placeholderText(@NotNull PsiAnnotation element) {
    return "/" + StringUtil.join(element.getParameterList().getAttributes(), value -> getMemberValueText(value.getValue()), ", ") + "/";
  }

  @NotNull
  private static String getMemberValueText(@Nullable PsiAnnotationMemberValue _memberValue) {
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
