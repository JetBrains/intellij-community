/*
 * User: anna
 * Date: 21-Dec-2007
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public abstract class RefJavaUtil {
  public abstract void addReferences(final PsiModifierListOwner psiFrom, final RefJavaElement ref, @Nullable PsiElement findIn);

  public abstract RefClass getTopLevelClass(RefElement refElement);

  public abstract boolean isInheritor(RefClass subClass, RefClass superClass);

  @Nullable //default package name
  public abstract String getPackageName(RefEntity refEntity);

  @Nullable
  public abstract RefClass getOwnerClass(RefManager refManager, PsiElement psiElement);

  @Nullable
  public abstract RefClass getOwnerClass(RefElement refElement);

  public abstract int compareAccess(String a1, String a2);

  public abstract String getAccessModifier(PsiModifierListOwner psiElement);

  public abstract void setAccessModifier(RefJavaElement refElement, String newAccess);

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
}