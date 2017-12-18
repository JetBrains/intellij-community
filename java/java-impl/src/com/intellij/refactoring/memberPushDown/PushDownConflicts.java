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
package com.intellij.refactoring.memberPushDown;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.containers.MultiMap;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class PushDownConflicts {
  private final PsiClass myClass;
  private final Set<PsiMember> myMovedMembers;
  private final Set<PsiMethod> myAbstractMembers;
  private final MultiMap<PsiElement, String> myConflicts;


  public PushDownConflicts(PsiClass aClass, MemberInfo[] memberInfos, MultiMap<PsiElement, String> conflicts) {
    myClass = aClass;

    myMovedMembers = new HashSet<>();
    myAbstractMembers = new HashSet<>();
    for (MemberInfo memberInfo : memberInfos) {
      final PsiMember member = memberInfo.getMember();
      if (memberInfo.isChecked() && (!(memberInfo.getMember() instanceof PsiClass) || memberInfo.getOverrides() == null)) {
        myMovedMembers.add(member);
        if (memberInfo.isToAbstract()) {
          myAbstractMembers.add((PsiMethod)member);
        }
      }
    }

    myConflicts = conflicts;
  }

  public boolean isAnyConflicts() {
    return !myConflicts.isEmpty();
  }

  public MultiMap<PsiElement, String> getConflicts() {
    return myConflicts;
  }

  public void checkSourceClassConflicts() {
    final PsiElement[] children = myClass.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiMember && !myMovedMembers.contains(child)) {
        child.accept(new UsedMovedMembersConflictsCollector(child));
      }
    }

    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(myClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE);
    if (annotation != null && myMovedMembers.contains(LambdaUtil.getFunctionalInterfaceMethod(myClass))) {
      myConflicts.putValue(annotation, RefactoringBundle.message("functional.interface.broken"));
    }
    boolean isAbstract = myClass.hasModifierProperty(PsiModifier.ABSTRACT);
    for (PsiMember member : myMovedMembers) {
      if (!member.hasModifierProperty(PsiModifier.STATIC)) {
        member.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (expression.getMethodExpression().getQualifierExpression() instanceof PsiSuperExpression) {
              final PsiMethod resolvedMethod = expression.resolveMethod();
              if (resolvedMethod != null) {
                final PsiClass resolvedClass = resolvedMethod.getContainingClass();
                if (resolvedClass != null && myClass.isInheritor(resolvedClass, true)) {
                  final PsiMethod methodBySignature = myClass.findMethodBySignature(resolvedMethod, false);
                  if (methodBySignature != null && !myMovedMembers.contains(methodBySignature)) {
                    myConflicts.putValue(expression, "Super method call will resolve to another method");
                  }
                }
              }
            }
          }
        });
      }
      if (!member.hasModifierProperty(PsiModifier.STATIC) && member instanceof PsiMethod && !myAbstractMembers.contains(member)) {
        Set<PsiClass> unrelatedDefaults = new LinkedHashSet<>();
        for (PsiMethod superMethod : ((PsiMethod)member).findSuperMethods()) {
          if (!isAbstract && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            myConflicts.putValue(member, "Non abstract " + RefactoringUIUtil.getDescription(myClass, false) + " will miss implementation of " + RefactoringUIUtil.getDescription(superMethod, false));
            break;
          }
          if (superMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
            unrelatedDefaults.add(superMethod.getContainingClass());
            if (unrelatedDefaults.size() > 1) {
              myConflicts.putValue(member, CommonRefactoringUtil.capitalize(RefactoringUIUtil.getDescription(myClass, false) + " will inherit unrelated defaults from " +
                                                                            StringUtil.join(unrelatedDefaults, aClass -> RefactoringUIUtil.getDescription(aClass, false)," and ")));
              break;
            }
          }
        }
      }
    }
  }

  public void checkTargetClassConflicts(final PsiElement targetElement, final PsiElement context) {
    if (targetElement instanceof PsiFunctionalExpression) {
      myConflicts.putValue(targetElement, RefactoringBundle.message("functional.interface.broken"));
      return;
    }

    final PsiClass targetClass = targetElement instanceof PsiClass ? (PsiClass)targetElement : null;
    if (targetClass != null) {
      for (final PsiMember movedMember : myMovedMembers) {
        checkMemberPlacementInTargetClassConflict(targetClass, movedMember);
      }
    }
    Members:
    for (PsiMember member : myMovedMembers) {
      if (member.hasModifierProperty(PsiModifier.STATIC)) continue;
      for (PsiReference ref : ReferencesSearch.search(member, member.getResolveScope(), false)) {
        final PsiElement element = ref.getElement();
        if (element instanceof PsiReferenceExpression) {
          if (myConflicts.containsKey(element)) continue;
          final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
          final PsiExpression qualifier = referenceExpression.getQualifierExpression();
          if (qualifier != null) {
            final PsiType qualifierType = qualifier.getType();
            PsiClass aClass = null;
            if (qualifierType instanceof PsiClassType) {
              aClass = ((PsiClassType)qualifierType).resolve();
            }
            else {
              if (qualifier instanceof PsiReferenceExpression) {
                final PsiElement resolved = ((PsiReferenceExpression)qualifier).resolve();
                if (resolved instanceof PsiClass) {
                  aClass = (PsiClass)resolved;
                }
              }
            }

            if (!InheritanceUtil.isInheritorOrSelf(aClass, targetClass, true)) {
              myConflicts.putValue(referenceExpression, RefactoringBundle.message("pushed.members.will.not.be.visible.from.certain.call.sites"));
              break Members;
            }
          }
        }
      }
    }
    RefactoringConflictsUtil.analyzeAccessibilityConflicts(myMovedMembers, targetClass, myConflicts, null, context, myAbstractMembers);
    
  }

  public void checkMemberPlacementInTargetClassConflict(final PsiClass targetClass, final PsiMember movedMember) {
    if (movedMember instanceof PsiField) {
      String name = movedMember.getName();
      final PsiField field = targetClass.findFieldByName(name, false);
      if (field != null) {
        String message = RefactoringBundle.message("0.already.contains.field.1", RefactoringUIUtil.getDescription(targetClass, false), CommonRefactoringUtil.htmlEmphasize(name));
        myConflicts.putValue(field, CommonRefactoringUtil.capitalize(message));
      }
    }
    else if (movedMember instanceof PsiMethod) {
      final PsiModifierList modifierList = movedMember.getModifierList();
      assert modifierList != null;
      if (!modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
        PsiMethod method = (PsiMethod)movedMember;
        final PsiMethod overrider = MethodSignatureUtil.findMethodBySuperMethod(targetClass, method, false);
        if (overrider != null) {
          String message = RefactoringBundle.message("0.is.already.overridden.in.1",
                                                     RefactoringUIUtil.getDescription(method, true), RefactoringUIUtil.getDescription(targetClass, false));
          myConflicts.putValue(overrider, CommonRefactoringUtil.capitalize(message));
        }
      }
    }
    else if (movedMember instanceof PsiClass) {
      PsiClass aClass = (PsiClass)movedMember;
      final String name = aClass.getName();
      final PsiClass[] allInnerClasses = targetClass.getAllInnerClasses();
      for (PsiClass innerClass : allInnerClasses) {
        if (innerClass.equals(movedMember)) continue;

        if (name.equals(innerClass.getName())) {
          String message = RefactoringBundle.message("0.already.contains.inner.class.named.1", RefactoringUIUtil.getDescription(targetClass, false),
                                                CommonRefactoringUtil.htmlEmphasize(name));
          myConflicts.putValue(innerClass, message);
        }
      }
    }

    if (movedMember.hasModifierProperty(PsiModifier.STATIC) &&
        PsiUtil.getEnclosingStaticElement(targetClass, null) == null &&
        !(targetClass.getParent() instanceof PsiFile)) {
      myConflicts.putValue(movedMember, "Static " + RefactoringUIUtil.getDescription(movedMember, false) + " can't be pushed to non-static " + RefactoringUIUtil.getDescription(targetClass, false));
    }
  }

  private class UsedMovedMembersConflictsCollector extends ClassMemberReferencesVisitor {
    private final PsiElement mySource;

    public UsedMovedMembersConflictsCollector(PsiElement source) {
      super(myClass);
      mySource = source;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if(myMovedMembers.contains(classMember) && !myAbstractMembers.contains(classMember)) {
        String message = RefactoringBundle.message("0.uses.1.which.is.pushed.down", RefactoringUIUtil.getDescription(mySource, false),
                                              RefactoringUIUtil.getDescription(classMember, false));
        message = CommonRefactoringUtil.capitalize(message);
        myConflicts.putValue(mySource, message);
      }
    }
  }
}
