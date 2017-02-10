/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 25-May-2010
 */
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SuppressWarningsFoldingBuilder extends FoldingBuilderEx {
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
          result.add(new FoldingDescriptor(annotation, annotation.getTextRange()));
        }
        super.visitAnnotation(annotation);
      }
    });
    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    final PsiElement element = node.getPsi();
    if (element instanceof PsiAnnotation) {
      return "/" + StringUtil.join(((PsiAnnotation)element).getParameterList().getAttributes(), value -> getMemberValueText(value.getValue()), ", ") + "/";
    }
    return element.getText();
  }

  private static String getMemberValueText(PsiAnnotationMemberValue memberValue) {
    if (memberValue instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)memberValue).getInitializers();
      return StringUtil.join(initializers, psiAnnotationMemberValue -> getMemberValueText(psiAnnotationMemberValue), ", ");
    }
    if (memberValue instanceof PsiLiteral) {
      final Object o = ((PsiLiteral)memberValue).getValue();
      if (o != null) {
        return o.toString();
      }
    }
    return memberValue != null ? memberValue.getText() : "";
  }


  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return JavaCodeFoldingSettings.getInstance().isCollapseSuppressWarnings();
  }
}
