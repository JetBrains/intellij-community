/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

public class ConstructorJavadocUsageInfo extends FixableUsageInfo {
  private final PsiMethod myMethod;
  private final JavaIntroduceParameterObjectClassDescriptor myDescriptor;

  public ConstructorJavadocUsageInfo(PsiMethod method, JavaIntroduceParameterObjectClassDescriptor descriptor) {
    super(method);
    myMethod = method;
    myDescriptor = descriptor;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    final PsiDocComment docComment = myMethod.getDocComment();
    if (docComment != null) {
      final List<PsiDocTag> mergedTags = new ArrayList<>();
      final PsiDocTag[] paramTags = docComment.findTagsByName("param");
      for (PsiDocTag paramTag : paramTags) {
        final PsiElement[] dataElements = paramTag.getDataElements();
        if (dataElements.length > 0) {
          if (dataElements[0] instanceof PsiDocParamRef) {
            final PsiReference reference = dataElements[0].getReference();
            if (reference != null) {
              final PsiElement resolve = reference.resolve();
              if (resolve instanceof PsiParameter) {
                final int parameterIndex = myMethod.getParameterList().getParameterIndex((PsiParameter)resolve);
                if (myDescriptor.getParameterInfo(parameterIndex) == null) continue;
              }
            }
          }
          mergedTags.add((PsiDocTag)paramTag.copy());
        }
      }

      PsiMethod compatibleParamObjectConstructor = null;
      final PsiMethod existingConstructor = myDescriptor.getExistingClassCompatibleConstructor();
      if (existingConstructor != null && existingConstructor.getDocComment() == null) {
        compatibleParamObjectConstructor = existingConstructor;
      }
      else if (!myDescriptor.isUseExistingClass()) {
        compatibleParamObjectConstructor = myDescriptor.getExistingClass().getConstructors()[0];
      }

      if (compatibleParamObjectConstructor != null) {
        PsiDocComment psiDocComment = JavaPsiFacade.getElementFactory(myMethod.getProject()).createDocCommentFromText("/**\n*/");
        psiDocComment =
          (PsiDocComment)compatibleParamObjectConstructor.addBefore(psiDocComment, compatibleParamObjectConstructor.getFirstChild());

        for (PsiDocTag tag : mergedTags) {
          psiDocComment.add(tag);
        }
      }
    }
  }
}
