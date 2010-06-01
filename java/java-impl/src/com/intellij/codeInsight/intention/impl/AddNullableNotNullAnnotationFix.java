/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:59 PM
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.intention.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

public class AddNullableNotNullAnnotationFix extends AddAnnotationFix {
  public AddNullableNotNullAnnotationFix(final String annotation, final String annotationToRemove) {
    super(annotation, annotationToRemove);
  }

  public AddNullableNotNullAnnotationFix(final String fqn, final PsiModifierListOwner owner, final String annotationToRemove) {
    super(fqn, owner, annotationToRemove);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
     if (!super.isAvailable(project, editor, element)) {
      return false;
    }
    PsiModifierListOwner owner = getContainer(element);
    if (owner == null || AnnotationUtil.isAnnotated(owner, getAnnotationsToRemove()[0], false)) {
      return false;
    }
    if (owner instanceof PsiMethod) {
      PsiType returnType = ((PsiMethod)owner).getReturnType();

      return returnType != null && !(returnType instanceof PsiPrimitiveType);
    }
    return true;
  }
}