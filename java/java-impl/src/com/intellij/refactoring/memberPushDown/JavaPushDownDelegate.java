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
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.intention.impl.CreateSubclassAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaPushDownDelegate extends PushDownDelegate<MemberInfo, PsiMember> {
  public static final Key<Boolean> REMOVE_QUALIFIER_KEY = Key.create("REMOVE_QUALIFIER_KEY");
  public static final Key<PsiClass> REPLACE_QUALIFIER_KEY = Key.create("REPLACE_QUALIFIER_KEY");

  private static final Logger LOG = Logger.getInstance("#" + JavaPushDownDelegate.class.getName());

  @Override
  public boolean isApplicableForSource(@NotNull PsiElement sourceClass) {
    return sourceClass.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  public List<PsiElement> findInheritors(PushDownData<MemberInfo, PsiMember> pushDownData) {
    final List<PsiElement> result = new ArrayList<>();
    final PsiClass aClass = (PsiClass)pushDownData.getSourceClass();
    ClassInheritorsSearch.search(aClass, false).forEach((iClass) -> {
      result.add(iClass);
      return true;
    });

    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
    if (interfaceMethod != null) {
      for (MemberInfo info : pushDownData.getMembersToMove()) {
        if (interfaceMethod == info.getMember()) {
          FunctionalExpressionSearch.search(aClass).forEach(expression -> {
            result.add(expression);
            return true;
          });
          break;
        }
      }
    }
    return result;
  }

  @Override
  public void checkSourceClassConflicts(PushDownData<MemberInfo, PsiMember> pushDownData, MultiMap<PsiElement, String> conflicts) {
    List<MemberInfo> toMove = pushDownData.getMembersToMove();
    new PushDownConflicts((PsiClass)pushDownData.getSourceClass(), toMove.toArray(new MemberInfo[0]), conflicts).checkSourceClassConflicts();
  }

  @Override
  public void checkTargetClassConflicts(@Nullable PsiElement targetClass,
                                        PushDownData<MemberInfo, PsiMember> pushDownData,
                                        MultiMap<PsiElement, String> conflicts,
                                        NewSubClassData subClassData) {
    List<MemberInfo> toMove = pushDownData.getMembersToMove();
    PsiElement context = targetClass;
    if (context == null) {
      assert subClassData != null;
      Object newClassContext = subClassData.getContext();
      if (newClassContext instanceof PsiElement) {
        context = (PsiElement)newClassContext;
      }
    }
    new PushDownConflicts((PsiClass)pushDownData.getSourceClass(), toMove.toArray(new MemberInfo[0]), conflicts)
      .checkTargetClassConflicts(targetClass, context);
  }

  @Override
  public NewSubClassData preprocessNoInheritorsFound(PsiElement sourceClass, String conflictDialogTitle) {
    final PsiClass aClass = (PsiClass)sourceClass;
    final PsiFile containingFile = aClass.getContainingFile();
    final boolean defaultPackage = StringUtil.isEmptyOrSpaces(containingFile instanceof PsiClassOwner ? ((PsiClassOwner)containingFile).getPackageName() : "");
    if (aClass.isEnum() || aClass.hasModifierProperty(PsiModifier.FINAL) || defaultPackage) {
      if (Messages.showOkCancelDialog((aClass.isEnum() ? "Enum " + aClass.getQualifiedName() + " doesn't have constants to inline to. "
                                                       : (defaultPackage ? "Class " : "Final class ") + aClass.getQualifiedName() + "does not have inheritors. ") +
                                      "Pushing members down will result in them being deleted. " +
                                      "Would you like to proceed?", conflictDialogTitle, Messages.getWarningIcon()) != Messages.OK) {
        return NewSubClassData.ABORT_REFACTORING;
      }
    } else {
      String noInheritors = aClass.isInterface() ?
                            RefactoringBundle.message("interface.0.does.not.have.inheritors", aClass.getQualifiedName()) :
                            RefactoringBundle.message("class.0.does.not.have.inheritors", aClass.getQualifiedName());
      final String message = noInheritors + "\n" + RefactoringBundle.message("push.down.will.delete.members");
      final int answer = Messages.showYesNoCancelDialog(message, conflictDialogTitle, Messages.getWarningIcon());
      if (answer == Messages.YES) {
        final CreateClassDialog classDialog = CreateSubclassAction.chooseSubclassToCreate(aClass);
        if (classDialog != null) {
          return new NewSubClassData(classDialog.getTargetDirectory(), classDialog.getClassName());
        } else {
          return NewSubClassData.ABORT_REFACTORING;
        }
      }
      else if (answer != Messages.NO) {
        return NewSubClassData.ABORT_REFACTORING;
      }
    }
    return null;
  }

  @Override
  public void prepareToPush(PushDownData<MemberInfo, PsiMember> pushDownData) {
    final Set<PsiMember> movedMembers = new HashSet<>();
    for (MemberInfoBase<? extends PsiElement> memberInfo : pushDownData.getMembersToMove()) {
      movedMembers.add((PsiMember)memberInfo.getMember());
    }

    for (MemberInfoBase<? extends PsiElement> memberInfo : pushDownData.getMembersToMove()) {
      final PsiElement member = memberInfo.getMember();
      member.accept(new JavaRecursiveElementVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          encodeRef((PsiClass)pushDownData.getSourceClass(), expression, movedMembers, expression);
          super.visitReferenceExpression(expression);
        }

        @Override public void visitNewExpression(PsiNewExpression expression) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            encodeRef((PsiClass)pushDownData.getSourceClass(), classReference, movedMembers, expression);
          }
          super.visitNewExpression(expression);
        }

        @Override
        public void visitTypeElement(final PsiTypeElement type) {
          final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
          if (referenceElement != null) {
            encodeRef((PsiClass)pushDownData.getSourceClass(), referenceElement, movedMembers, type);
          }
          super.visitTypeElement(type);
        }
      });
      ChangeContextUtil.encodeContextInfo(member, false);
    }
  }

  @Override
  public void pushDownToClass(PsiElement targetElement, PushDownData<MemberInfo, PsiMember> pushDownData) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(pushDownData.getSourceClass().getProject()).getElementFactory();
    final PsiClass targetClass = targetElement instanceof PsiClass ? (PsiClass)targetElement : null;
    if (targetClass == null) {
      return;
    }
    final PsiClass sourceClass = (PsiClass)pushDownData.getSourceClass();
    final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(sourceClass, targetClass, PsiSubstitutor.EMPTY);
    for (MemberInfoBase<? extends PsiElement> memberInfo : pushDownData.getMembersToMove()) {
      PsiMember member = (PsiMember)memberInfo.getMember();
      final List<PsiReference> refsToRebind = new ArrayList<>();
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
          final PsiMethod oldMethod = (PsiMethod)memberInfo.getMember();
          if (sourceClass.isInterface() && !targetClass.isInterface()) {
            PsiUtil.setModifierProperty(newMember, PsiModifier.PUBLIC, true);
            if (oldMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
              RefactoringUtil.makeMethodAbstract(targetClass, (PsiMethod)newMember);
            }
            else {
              PsiUtil.setModifierProperty(newMember, PsiModifier.DEFAULT, false);
            }
          }

          if (memberInfo.isToAbstract()) {
            if (newMember.hasModifierProperty(PsiModifier.PRIVATE)) {
              PsiUtil.setModifierProperty(newMember, PsiModifier.PROTECTED, true);
            }

            pushDownData.getCommentPolicy().processNewJavaDoc(((PsiMethod)newMember).getDocComment());
            OverrideImplementUtil.annotateOnOverrideImplement((PsiMethod)newMember, targetClass, (PsiMethod)memberInfo.getMember());
          }
        }
        else { //abstract method: remove @Override
          if (!memberInfo.isToAbstract()) {
            final PsiAnnotation annotation = AnnotationUtil.findAnnotation(methodBySignature, "java.lang.Override");
            if (annotation != null && !leaveOverrideAnnotation(sourceClass, substitutor, method)) {
              annotation.delete();
            }
          }
          final PsiDocComment oldDocComment = method.getDocComment();
          if (oldDocComment != null) {
            final PsiDocComment docComment = methodBySignature.getDocComment();
            final int policy = pushDownData.getCommentPolicy().getJavaDocPolicy();
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
        if (sourceClass.isInterface() && !targetClass.isInterface()) {
          PsiUtil.setModifierProperty(member, PsiModifier.PUBLIC, true);
        }
        if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
          final PsiClass psiClass = (PsiClass)memberInfo.getMember();
          PsiClassType classType = null;
          if (!targetClass.isInheritor(psiClass, false)) {
            final PsiClassType[] types = ((MemberInfo)memberInfo).getSourceReferenceList().getReferencedTypes();
            for (PsiClassType type : types) {
              if (type.resolve() == psiClass) {
                classType = (PsiClassType)substitutor.substitute(type);
              }
            }
            PsiJavaCodeReferenceElement classRef = classType != null ? factory.createReferenceElementByType(classType) : factory.createClassReferenceElement(psiClass);
            if (psiClass.isInterface()) {
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

      if (newMember != null) {
        decodeRefs(sourceClass, newMember, targetClass);
        //rebind imports first
        Collections.sort(refsToRebind, Comparator.comparing(PsiReference::getElement, PsiUtil.BY_POSITION));
        for (PsiReference psiReference : refsToRebind) {
          JavaCodeStyleManager.getInstance(sourceClass.getProject()).shortenClassReferences(psiReference.bindToElement(newMember));
        }
        final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(newMember.getProject());
        ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(sourceClass, newMember);
      }
    }
  }

  @Override
  public void removeFromSourceClass(PushDownData<MemberInfo, PsiMember> pushDownData) {
    for (MemberInfoBase<? extends PsiElement> memberInfo : pushDownData.getMembersToMove()) {
      final PsiElement member = memberInfo.getMember();

      if (member instanceof PsiField) {
        member.delete();
      }
      else if (member instanceof PsiMethod) {
        if (memberInfo.isToAbstract()) {
          final PsiMethod method = (PsiMethod)member;
          if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
            PsiUtil.setModifierProperty(method, PsiModifier.PROTECTED, true);
          }
          if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
            PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, false);
          }
          RefactoringUtil.makeMethodAbstract((PsiClass)pushDownData.getSourceClass(), method);
          pushDownData.getCommentPolicy().processOldJavaDoc(method.getDocComment());
        }
        else {
          member.delete();
        }
      }
      else if (member instanceof PsiClass) {
        if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
          RefactoringUtil.removeFromReferenceList(((PsiClass)pushDownData.getSourceClass()).getImplementsList(), (PsiClass)member);
        }
        else {
          member.delete();
        }
      }
    }
  }

  @Override
  public PsiElement createSubClass(PsiElement aClass, NewSubClassData subClassData) {
    return CreateSubclassAction.createSubclass((PsiClass)aClass, (PsiDirectory)subClassData.getContext(), subClassData.getNewClassName());
  }

  private static boolean leaveOverrideAnnotation(PsiClass aClass, PsiSubstitutor substitutor, PsiMethod method) {
    final PsiMethod methodBySignature = MethodSignatureUtil.findMethodBySignature(aClass, method.getSignature(substitutor), false);
    if (methodBySignature == null) return false;
    final PsiMethod[] superMethods = methodBySignature.findDeepestSuperMethods();
    if (superMethods.length == 0) return false;
    final boolean is15 = !PsiUtil.isLanguageLevel6OrHigher(methodBySignature);
    if (is15) {
      for (PsiMethod psiMethod : superMethods) {
        final PsiClass psiClass = psiMethod.getContainingClass();
        if (psiClass != null && psiClass.isInterface()) {
          return false;
        }
      }
    }
    return true;
  }
  
  private static void encodeRef(PsiClass aClass,
                                final PsiJavaCodeReferenceElement expression,
                                final Set<PsiMember> movedMembers,
                                final PsiElement toPut) {
    final PsiElement resolved = expression.resolve();
    if (resolved == null) return;
    final PsiElement qualifier = expression.getQualifier();
    for (PsiMember movedMember : movedMembers) {
      if (movedMember.equals(resolved)) {
        if (qualifier == null) {
          toPut.putCopyableUserData(REMOVE_QUALIFIER_KEY, Boolean.TRUE);
        } else {
          if (qualifier instanceof PsiJavaCodeReferenceElement &&
              ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(aClass)) {
            toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, aClass);
          }
        }
      } else if (movedMember instanceof PsiClass && PsiTreeUtil.getParentOfType(resolved, PsiClass.class, false) == movedMember) {
        if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(movedMember)) {
          toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, (PsiClass)movedMember);
        }
      } else {
        if (qualifier instanceof PsiThisExpression) {
          final PsiJavaCodeReferenceElement qElement = ((PsiThisExpression)qualifier).getQualifier();
          if (qElement != null && qElement.isReferenceTo(aClass)) {
            toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, aClass);
          }
        }
      }
    }
  }

  private static void decodeRefs(PsiClass sourceClass, final PsiMember member, final PsiClass targetClass) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(sourceClass.getProject()).getElementFactory();
    member.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        decodeRef(sourceClass, expression, factory, targetClass, expression);
        super.visitReferenceExpression(expression);
      }

      @Override public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference != null) decodeRef(sourceClass, classReference, factory, targetClass, expression);
        super.visitNewExpression(expression);
      }

      @Override
      public void visitTypeElement(final PsiTypeElement type) {
        final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
        if (referenceElement != null)  decodeRef(sourceClass, referenceElement, factory, targetClass, type);
        super.visitTypeElement(type);
      }
    });
  }

  private static void decodeRef(PsiClass sourceClass,
                                final PsiJavaCodeReferenceElement ref,
                                final PsiElementFactory factory,
                                final PsiClass targetClass,
                                final PsiElement toGet) {
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
              qualifier.replace(factory.createReferenceElementByType(factory.createType(psiClass)));
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
