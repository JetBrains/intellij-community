/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeContextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ChangeContextUtil");

  public static final Key<String> ENCODED_KEY = Key.create("ENCODED_KEY");
  public static final Key<PsiClass> THIS_QUALIFIER_CLASS_KEY = Key.create("THIS_QUALIFIER_CLASS_KEY");
  public static final Key<PsiMember> REF_MEMBER_KEY = Key.create("REF_MEMBER_KEY");
  public static final Key<Boolean> CAN_REMOVE_QUALIFIER_KEY = Key.create("CAN_REMOVE_QUALIFIER_KEY");
  public static final Key<PsiClass> REF_CLASS_KEY = Key.create("REF_CLASS_KEY");
  public static final Key<PsiClass> REF_MEMBER_THIS_CLASS_KEY = Key.create("REF_MEMBER_THIS_CLASS_KEY");

  private ChangeContextUtil() {}

  public static void encodeContextInfo(PsiElement scope, boolean includeRefClasses) {
    encodeContextInfo(scope, scope, includeRefClasses, true);
  }

  public static void encodeContextInfo(PsiElement scope, boolean includeRefClasses, boolean canChangeQualifier) {
    encodeContextInfo(scope, scope, includeRefClasses, canChangeQualifier);
  }

  private static void encodeContextInfo(PsiElement scope,
                                        PsiElement topLevelScope,
                                        boolean includeRefClasses,
                                        boolean canChangeQualifier) {
    if (scope instanceof PsiThisExpression){
      scope.putCopyableUserData(ENCODED_KEY, "");

      PsiThisExpression thisExpr = (PsiThisExpression)scope;
      final PsiJavaCodeReferenceElement qualifier = thisExpr.getQualifier();
      if (qualifier == null){
        PsiClass thisClass = RefactoringChangeUtil.getThisClass(thisExpr);
        if (thisClass != null && !(thisClass instanceof PsiAnonymousClass)){
          thisExpr.putCopyableUserData(THIS_QUALIFIER_CLASS_KEY, thisClass);
        }
      }
      else {
        final PsiElement resolved = qualifier.resolve();
        if (resolved instanceof PsiClass && resolved == topLevelScope) {
          thisExpr.putCopyableUserData(THIS_QUALIFIER_CLASS_KEY, (PsiClass)topLevelScope);
        }
      }
    }
    else if (scope instanceof PsiReferenceExpression){
      scope.putCopyableUserData(ENCODED_KEY, "");

      PsiReferenceExpression refExpr = (PsiReferenceExpression)scope;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null){
        final JavaResolveResult resolveResult = refExpr.advancedResolve(false);
        final PsiElement refElement = resolveResult.getElement();
        if (refElement != null && !PsiTreeUtil.isAncestor(topLevelScope, refElement, false)){
          if (refElement instanceof PsiClass){
            if (includeRefClasses){
              refExpr.putCopyableUserData(REF_CLASS_KEY, (PsiClass)refElement);
            }
          }
          else if (refElement instanceof PsiMember){
            refExpr.putCopyableUserData(REF_MEMBER_KEY, ( (PsiMember)refElement));
            final PsiElement resolveScope = resolveResult.getCurrentFileResolveScope();
            if (resolveScope instanceof PsiClass && !PsiTreeUtil.isAncestor(topLevelScope, resolveScope, false)) {
              refExpr.putCopyableUserData(REF_MEMBER_THIS_CLASS_KEY, (PsiClass)resolveScope);
            }
          }
        }
      }
      else if (canChangeQualifier) {
        refExpr.putCopyableUserData(CAN_REMOVE_QUALIFIER_KEY, canRemoveQualifier(refExpr));
      }
    }
    else if (includeRefClasses) {
      PsiReference ref = scope.getReference();
      if (ref != null){
        scope.putCopyableUserData(ENCODED_KEY, "");

        PsiElement refElement = ref.resolve();
        if (refElement instanceof PsiClass && !PsiTreeUtil.isAncestor(topLevelScope, refElement, false)){
          scope.putCopyableUserData(REF_CLASS_KEY, (PsiClass)refElement);
        }
      }
    }

    for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
      encodeContextInfo(child, topLevelScope, includeRefClasses, canChangeQualifier);
    }
  }

  @NotNull
  public static PsiElement decodeContextInfo(@NotNull PsiElement scope,
                                             @Nullable PsiClass thisClass,
                                             @Nullable PsiExpression thisAccessExpr) throws IncorrectOperationException {
    if (scope.getCopyableUserData(ENCODED_KEY) != null) {
      scope.putCopyableUserData(ENCODED_KEY, null);

      if (scope instanceof PsiThisExpression) {
        PsiThisExpression thisExpr = (PsiThisExpression)scope;
        scope = decodeThisExpression(thisExpr, thisClass, thisAccessExpr);
      }
      else if (scope instanceof PsiReferenceExpression) {
        scope = decodeReferenceExpression((PsiReferenceExpression)scope, thisAccessExpr, thisClass);
      }
      else {
        PsiClass refClass = scope.getCopyableUserData(REF_CLASS_KEY);
        scope.putCopyableUserData(REF_CLASS_KEY, null);

        if (refClass != null && refClass.isValid()) {
          PsiReference ref = scope.getReference();
          if (ref != null) {
            final String qualifiedName = refClass.getQualifiedName();
            if (qualifiedName != null) {
              if (JavaPsiFacade.getInstance(refClass.getProject()).findClass(qualifiedName, scope.getResolveScope()) != null) {
                scope = ref.bindToElement(refClass);
              }
            }
          }
        }
      }
    }

    if (scope instanceof PsiClass) {
      if (thisAccessExpr != null) {
        thisAccessExpr = (PsiExpression)qualifyThis(thisAccessExpr, thisClass);
      }
    }

    PsiElement child = scope.getFirstChild();
    while (child != null) {
      child = decodeContextInfo(child, thisClass, thisAccessExpr).getNextSibling();
    }

    return scope;
  }

  private static PsiElement decodeThisExpression(PsiThisExpression thisExpr,
                                                 PsiClass thisClass,
                                                 PsiExpression thisAccessExpr) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement qualifier = thisExpr.getQualifier();
    PsiClass encodedQualifierClass = thisExpr.getCopyableUserData(THIS_QUALIFIER_CLASS_KEY);
    thisExpr.putCopyableUserData(THIS_QUALIFIER_CLASS_KEY, null);
    if (qualifier == null){
      if (encodedQualifierClass != null && encodedQualifierClass.isValid()){
        if (encodedQualifierClass.equals(thisClass) && thisAccessExpr != null && thisAccessExpr.isValid()){
          return thisExpr.replace(thisAccessExpr);
        }
      }
    }
    else {
      PsiClass qualifierClass = (PsiClass)qualifier.resolve();
      if (encodedQualifierClass == qualifierClass && thisClass != null) {
        qualifier.bindToElement(thisClass);
      }
      else {
        if (qualifierClass != null) {
          if (qualifierClass.equals(thisClass) && thisAccessExpr != null && thisAccessExpr.isValid()) {
            return thisExpr.replace(thisAccessExpr);
          }
        }
      }
    }
    return thisExpr;
  }

  private static PsiReferenceExpression decodeReferenceExpression(@NotNull PsiReferenceExpression refExpr,
                                                                  PsiExpression thisAccessExpr,
                                                                  PsiClass thisClass) throws IncorrectOperationException {
    PsiManager manager = refExpr.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    PsiExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null){
      PsiMember refMember = refExpr.getCopyableUserData(REF_MEMBER_KEY);
      refExpr.putCopyableUserData(REF_MEMBER_KEY, null);

      if (refMember != null && refMember.isValid()){
        PsiClass containingClass = refMember.getContainingClass();
        if (refMember.hasModifierProperty(PsiModifier.STATIC)){
          PsiElement refElement = refExpr.resolve();
          if (!manager.areElementsEquivalent(refMember, refElement)){
            final PsiClass currentClass = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
            if (currentClass == null || !InheritanceUtil.isInheritorOrSelf(currentClass, containingClass, true)) {
              refExpr.setQualifierExpression(factory.createReferenceExpression(containingClass));
            }
          }
        }
        else {
          final PsiClass realParentClass = refExpr.getCopyableUserData(REF_MEMBER_THIS_CLASS_KEY);
          refExpr.putCopyableUserData(REF_MEMBER_THIS_CLASS_KEY, null);
          if (thisAccessExpr != null && thisClass != null && realParentClass != null &&
              InheritanceUtil.isInheritorOrSelf(thisClass, realParentClass, true)) {
            boolean needQualifier = true;
            PsiElement refElement = refExpr.resolve();
            if (refMember.equals(refElement) ||
                (refElement instanceof PsiMethod && refMember instanceof PsiMethod && ArrayUtil.find(((PsiMethod)refElement).findSuperMethods(), refMember) > -1)){
              if (thisAccessExpr instanceof PsiThisExpression && ((PsiThisExpression)thisAccessExpr).getQualifier() == null) {
                //Trivial qualifier
                needQualifier = false;
              }
              else {
                final PsiClass currentClass = findThisClass(refExpr, refMember);
                if (thisAccessExpr instanceof PsiThisExpression){
                  PsiJavaCodeReferenceElement thisQualifier = ((PsiThisExpression)thisAccessExpr).getQualifier();
                  PsiClass thisExprClass = thisQualifier != null
                                           ? (PsiClass)thisQualifier.resolve()
                                           : RefactoringChangeUtil.getThisClass(refExpr);
                  if (currentClass.equals(thisExprClass) || thisExprClass.isInheritor(realParentClass, true)){ // qualifier is not necessary
                    needQualifier = false;
                  }
                }
              }
            }

            if (needQualifier){
              refExpr.setQualifierExpression(thisAccessExpr);
            }
          }
          else if (thisClass != null && realParentClass != null && PsiTreeUtil.isAncestor(realParentClass, thisClass, true)) {
            PsiElement refElement = refExpr.resolve();
            if (refElement != null && !manager.areElementsEquivalent(refMember, refElement)) {
              refExpr = RefactoringChangeUtil.qualifyReference(refExpr, refMember, null);
            }
          }
        }
      }
      else {
        PsiClass refClass = refExpr.getCopyableUserData(REF_CLASS_KEY);
        refExpr.putCopyableUserData(REF_CLASS_KEY, null);
        if (refClass != null && refClass.isValid()){
          refExpr = (PsiReferenceExpression)refExpr.bindToElement(refClass);
        }
      }
    }
    else{
      Boolean couldRemove = refExpr.getCopyableUserData(CAN_REMOVE_QUALIFIER_KEY);
      refExpr.putCopyableUserData(CAN_REMOVE_QUALIFIER_KEY, null);

      if (couldRemove == Boolean.FALSE && canRemoveQualifier(refExpr)){
        PsiReferenceExpression newRefExpr = (PsiReferenceExpression)factory.createExpressionFromText(
          refExpr.getReferenceName(), null);
        refExpr = (PsiReferenceExpression)refExpr.replace(newRefExpr);
      }
    }
    return refExpr;
  }

  private static PsiClass findThisClass(PsiReferenceExpression refExpr, PsiMember refMember) {
    LOG.assertTrue(refExpr.getQualifierExpression() == null);
    final PsiClass refMemberClass = refMember.getContainingClass();
    if (refMemberClass == null) return null;
    PsiElement parent = refExpr.getContext();
    while(parent != null){
      if (parent instanceof PsiClass){
        if (parent.equals(refMemberClass) || ((PsiClass)parent).isInheritor(refMemberClass, true)){
          return (PsiClass)parent;
        }
      }
      parent = parent.getContext();
    }

    return refMemberClass;
  }

  public static boolean canRemoveQualifier(PsiReferenceExpression refExpr) {
    try{
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) return false;
      if (refExpr.getTypeParameters().length > 0) return false;
      PsiElement qualifierRefElement = ((PsiReferenceExpression)qualifier).resolve();
      if (!(qualifierRefElement instanceof PsiClass)) return false;
      PsiElement refElement = refExpr.resolve();
      if (refElement == null) return false;
      PsiElementFactory factory = JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory();
      if (refExpr.getParent() instanceof PsiMethodCallExpression){
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)refExpr.getParent();
        PsiMethodCallExpression newMethodCall = (PsiMethodCallExpression)factory.createExpressionFromText(
          refExpr.getReferenceName() + "()", refExpr);
        newMethodCall.getArgumentList().replace(methodCall.getArgumentList());
        PsiElement newRefElement = newMethodCall.getMethodExpression().resolve();
        return refElement.equals(newRefElement);
      }
      else if (refExpr instanceof PsiMethodReferenceExpression) {
        return false;
      }
      else {
        PsiReferenceExpression newRefExpr = (PsiReferenceExpression)factory.createExpressionFromText(
          refExpr.getReferenceName(), refExpr);
        PsiElement newRefElement = newRefExpr.resolve();
        return refElement.equals(newRefElement);
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
      return false;
    }
  }

  private static PsiElement qualifyThis(PsiElement scope, PsiClass thisClass) throws IncorrectOperationException {
    if (scope instanceof PsiThisExpression){
      PsiThisExpression thisExpr = (PsiThisExpression)scope;
      if (thisExpr.getQualifier() == null){
        if (thisClass instanceof PsiAnonymousClass) return null;
        PsiThisExpression qualifiedThis = RefactoringChangeUtil.createThisExpression(thisClass.getManager(), thisClass);
        if (thisExpr.getParent() != null) {
          return thisExpr.replace(qualifiedThis);
        } else {
          return qualifiedThis;
        }
      }
    }
    else if (!(scope instanceof PsiClass)){
      for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
        if (qualifyThis(child, thisClass) == null) return null;
      }
    }
    return scope;
  }

  public static void clearContextInfo(PsiElement scope) {
    scope.putCopyableUserData(THIS_QUALIFIER_CLASS_KEY, null);
    scope.putCopyableUserData(REF_MEMBER_KEY, null);
    scope.putCopyableUserData(CAN_REMOVE_QUALIFIER_KEY, null);
    for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
      clearContextInfo(child);
    }
  }
}
