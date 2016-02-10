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
package com.intellij.refactoring.memberPushDown;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.refactoring.memberPushDown.PushDownProcessor.REMOVE_QUALIFIER_KEY;
import static com.intellij.refactoring.memberPushDown.PushDownProcessor.REPLACE_QUALIFIER_KEY;

public class JavaPushDownLanguageHelper extends PushDownLanguageHelper<MemberInfo> {
  private static final Logger LOG = Logger.getInstance(JavaPushDownLanguageHelper.class);

  private static boolean leaveOverrideAnnotation(PsiClass sourceClass, PsiSubstitutor substitutor, PsiMethod method) {
    final PsiMethod methodBySignature = MethodSignatureUtil.findMethodBySignature(sourceClass, method.getSignature(substitutor), false);
    if (methodBySignature == null) return false;
    final PsiMethod[] superMethods = methodBySignature.findDeepestSuperMethods();
    if (superMethods.length == 0) return false;
    final boolean is15 = !PsiUtil.isLanguageLevel6OrHigher(methodBySignature);
    if (is15) {
      for (PsiMethod psiMethod : superMethods) {
        final PsiClass aClass = psiMethod.getContainingClass();
        if (aClass != null && aClass.isInterface()) {
          return false;
        }
      }
    }
    return true;
  }

  private static void decodeRef(PushDownContext context,
                                PsiJavaCodeReferenceElement ref,
                                PsiElementFactory factory,
                                PsiClass targetClass,
                                PsiElement toGet) {
    PsiClass sourceClass = context.getSourceClass();
    try {
      if (toGet.getCopyableUserData(REMOVE_QUALIFIER_KEY) != null) {
        toGet.putCopyableUserData(REMOVE_QUALIFIER_KEY, null);
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier != null) qualifier.delete();
      }
      else {
        PsiClass psiClass = toGet.getCopyableUserData(REPLACE_QUALIFIER_KEY);
        if (psiClass != null) {
          toGet.putCopyableUserData(REPLACE_QUALIFIER_KEY, null);
          PsiElement qualifier = ref.getQualifier();
          if (qualifier != null) {

            if (psiClass == sourceClass) {
              psiClass = targetClass;
            } else if (psiClass.getContainingClass() == sourceClass) {
              psiClass = targetClass.findInnerClassByName(psiClass.getName(), false);
              LOG.assertTrue(psiClass != null);
            }

            if (!(qualifier instanceof PsiThisExpression) && ref instanceof PsiReferenceExpression) {
              ((PsiReferenceExpression)ref).setQualifierExpression(factory.createReferenceExpression(psiClass));
            }
            else {
              if (qualifier instanceof PsiThisExpression) {
                qualifier = ((PsiThisExpression)qualifier).getQualifier();
              }
              if (qualifier != null) {
                qualifier.replace(factory.createReferenceElementByType(factory.createType(psiClass)));
              }
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public void postprocessMember(@NotNull PushDownContext context, @NotNull PsiMember newMember, @NotNull PsiClass targetClass) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(newMember.getProject()).getElementFactory();
    newMember.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        decodeRef(context, expression, factory, targetClass, expression);
        super.visitReferenceExpression(expression);
      }

      @Override public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference != null) decodeRef(context, classReference, factory, targetClass, expression);
        super.visitNewExpression(expression);
      }

      @Override
      public void visitTypeElement(final PsiTypeElement type) {
        final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
        if (referenceElement != null)  decodeRef(context, referenceElement, factory, targetClass, type);
        super.visitTypeElement(type);
      }
    });
  }

  @Nullable
  @Override
  public PsiMember pushDownToClass(@NotNull PushDownContext context,
                                   @NotNull MemberInfo memberInfo,
                                   @NotNull PsiClass targetClass,
                                   @NotNull PsiSubstitutor substitutor,
                                   @NotNull List<PsiReference> refsToRebind) {
    PsiClass sourceClass = context.getSourceClass();
    DocCommentPolicy<PsiComment> docCommentPolicy = context.getDocCommentPolicy();
    PsiMember member = memberInfo.getMember();

    PsiElementFactory factory = JavaPsiFacade.getInstance(sourceClass.getProject()).getElementFactory();

    final PsiModifierList list = member.getModifierList();
    LOG.assertTrue(list != null);
    if (list.hasModifierProperty(PsiModifier.STATIC)) {
      for (final PsiReference reference : ReferencesSearch.search(member)) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiReferenceExpression) {
          final PsiExpression qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
          if (qualifierExpression instanceof PsiReferenceExpression && !(((PsiReferenceExpression)qualifierExpression).resolve() instanceof PsiClass)) {
            continue;
          }
        }
        refsToRebind.add(reference);
      }
    }
    member = (PsiMember)member.copy();
    RefactoringUtil.replaceMovedMemberTypeParameters(member, PsiUtil.typeParametersIterable(sourceClass), substitutor, factory);
    PsiMember newMember = null;
    if (member instanceof PsiField) {
      ((PsiField)member).normalizeDeclaration();
      if (sourceClass.isInterface() && !targetClass.isInterface()) {
        PsiUtil.setModifierProperty(member, PsiModifier.PUBLIC, true);
        PsiUtil.setModifierProperty(member, PsiModifier.STATIC, true);
        PsiUtil.setModifierProperty(member, PsiModifier.FINAL, true);
      }
      newMember = (PsiMember)targetClass.add(member);
    }
    else if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)member;
      PsiMethod methodBySignature = MethodSignatureUtil.findMethodBySuperSignature(targetClass, method.getSignature(substitutor), false);
      if (methodBySignature == null) {
        newMember = (PsiMethod)targetClass.add(method);
        if (sourceClass.isInterface()) {
          if (!targetClass.isInterface()) {
            PsiUtil.setModifierProperty(newMember, PsiModifier.PUBLIC, true);
            if (newMember.hasModifierProperty(PsiModifier.DEFAULT)) {
              PsiUtil.setModifierProperty(newMember, PsiModifier.DEFAULT, false);
            }
            else {
              PsiUtil.setModifierProperty(newMember, PsiModifier.ABSTRACT, true);
            }
          }
        }
        else if (memberInfo.isToAbstract()) {
          if (newMember.hasModifierProperty(PsiModifier.PRIVATE)) {
            PsiUtil.setModifierProperty(newMember, PsiModifier.PROTECTED, true);
          }
          docCommentPolicy.processNewJavaDoc(((PsiMethod)newMember).getDocComment());
        }
        if (memberInfo.isToAbstract()) {
          OverrideImplementUtil.annotateOnOverrideImplement((PsiMethod)newMember, targetClass, (PsiMethod)memberInfo.getMember());
        }
      }
      else { //abstract method: remove @Override
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(methodBySignature, "java.lang.Override");
        if (annotation != null && !leaveOverrideAnnotation(sourceClass, substitutor, method)) {
          annotation.delete();
        }
        final PsiDocComment oldDocComment = method.getDocComment();
        if (oldDocComment != null) {
          final PsiDocComment docComment = methodBySignature.getDocComment();
          final int policy = docCommentPolicy.getJavaDocPolicy();
          if (policy == DocCommentPolicy.COPY || policy == DocCommentPolicy.MOVE) {
            if (docComment != null) {
              docComment.replace(oldDocComment);
            }
            else {
              methodBySignature.getParent().addBefore(oldDocComment, methodBySignature);
            }
          }
        }
      }
    }
    else if (member instanceof PsiClass) {
      if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
        final PsiClass aClass = (PsiClass)memberInfo.getMember();
        PsiClassType classType = null;
        if (!targetClass.isInheritor(aClass, false)) {
          final PsiClassType[] types = memberInfo.getSourceReferenceList().getReferencedTypes();
          for (PsiClassType type : types) {
            if (type.resolve() == aClass) {
              classType = (PsiClassType)substitutor.substitute(type);
            }
          }
          PsiJavaCodeReferenceElement classRef = classType != null ? factory.createReferenceElementByType(classType) : factory.createClassReferenceElement(aClass);
          if (aClass.isInterface()) {
            targetClass.getImplementsList().add(classRef);
          } else {
            targetClass.getExtendsList().add(classRef);
          }
        }
      }
      else {
        newMember = (PsiMember)targetClass.add(member);
      }
    }

    return newMember;
  }

}
