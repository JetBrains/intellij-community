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
 * Date: 21-Dec-2007
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RefJavaUtil {
  public abstract void addReferences(@NotNull PsiModifierListOwner psiFrom, @NotNull RefJavaElement ref, @Nullable PsiElement findIn);

  public abstract RefClass getTopLevelClass(@NotNull RefElement refElement);

  public abstract boolean isInheritor(@NotNull RefClass subClass, RefClass superClass);

  @Nullable //default package name
  public abstract String getPackageName(RefEntity refEntity);

  @Nullable
  public abstract RefClass getOwnerClass(RefManager refManager, PsiElement psiElement);

  @Nullable
  public abstract RefClass getOwnerClass(RefElement refElement);

  public abstract int compareAccess(String a1, String a2);

  @NotNull
  public abstract String getAccessModifier(@NotNull PsiModifierListOwner psiElement);

  public abstract void setAccessModifier(@NotNull RefJavaElement refElement, @NotNull String newAccess);

  public abstract void setIsStatic(RefJavaElement refElement, boolean isStatic);

  public abstract void setIsFinal(RefJavaElement refElement, boolean isFinal);

  public abstract boolean isMethodOnlyCallsSuper(final PsiMethod derivedMethod);

  public static boolean isDeprecated(PsiElement psiResolved) {
    return psiResolved instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)psiResolved).isDeprecated();
  }

  @Nullable
  public static RefPackage getPackage(RefEntity refEntity) {
   while (refEntity != null && !(refEntity instanceof RefPackage)) refEntity = refEntity.getOwner();

   return (RefPackage)refEntity;
 }

  public static RefJavaUtil getInstance() {
    return ServiceManager.getService(RefJavaUtil.class);
  }

  public abstract boolean isCallToSuperMethod(PsiExpression expression, PsiMethod method);

  public abstract void addTypeReference(PsiElement psiElement, PsiType psiType, RefManager refManager);
  public abstract void addTypeReference(PsiElement psiElement, PsiType psiType, RefManager refManager, @Nullable RefJavaElement refElement);
}
