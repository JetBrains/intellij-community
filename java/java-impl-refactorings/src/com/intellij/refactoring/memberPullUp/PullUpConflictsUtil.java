// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPullUp;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PullUpConflictsUtil {
  private PullUpConflictsUtil() {}

  public static MultiMap<PsiElement, String> checkConflicts(MemberInfoBase<? extends PsiMember>[] infos,
                                                            PsiClass subclass,
                                                            @Nullable PsiClass superClass,
                                                            @NotNull PsiPackage targetPackage,
                                                            @NotNull PsiDirectory targetDirectory,
                                                            final InterfaceContainmentVerifier interfaceContainmentVerifier) {
    return checkConflicts(infos, subclass, superClass, targetPackage, targetDirectory, interfaceContainmentVerifier, true);
  }

  public static MultiMap<PsiElement, String> checkConflicts(final MemberInfoBase<? extends PsiMember>[] infos,
                                                            @NotNull final PsiClass subclass,
                                                            @Nullable PsiClass superClass,
                                                            @NotNull final PsiPackage targetPackage,
                                                            @NotNull PsiDirectory targetDirectory,
                                                            final InterfaceContainmentVerifier interfaceContainmentVerifier,
                                                            boolean movedMembers2Super) {
    final Set<PsiMember> movedMembers = new HashSet<>();
    final Set<PsiMethod> abstractMethods = new HashSet<>();
    final boolean isInterfaceTarget;
    final PsiElement targetRepresentativeElement;
    if (superClass != null) {
      isInterfaceTarget = superClass.isInterface();
      targetRepresentativeElement = superClass;
    }
    else {
      isInterfaceTarget = false;
      targetRepresentativeElement = targetDirectory;
    }
    for (MemberInfoBase<? extends PsiMember> info : infos) {
      PsiMember member = info.getMember();
      if (member instanceof PsiMethod) {
        if (!info.isToAbstract()) {
          movedMembers.add(member);
        }
        else {
          abstractMethods.add((PsiMethod)member);
        }
      }
      else {
        movedMembers.add(member);
      }
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final Set<PsiMethod> abstrMethods = new HashSet<>(abstractMethods);
    if (superClass != null && movedMembers2Super) {
      for (PsiMethod method : subclass.getMethods()) {
        if (!movedMembers.contains(method) && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
          if (method.findSuperMethods(superClass).length > 0) {
            abstrMethods.add(method);
          }
        }
      }

      List<PsiMethod> newAbstractMethods = newAbstractMethodInSuper(infos);
      if (!newAbstractMethods.isEmpty()) {
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(superClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE);
        if (annotation != null) {
          conflicts.putValue(annotation, JavaRefactoringBundle.message("functional.interface.broken"));
        } else {
          final PsiFunctionalExpression functionalExpression = FunctionalExpressionSearch.search(superClass).findFirst();
          if (functionalExpression != null) {
            conflicts.putValue(functionalExpression, JavaRefactoringBundle.message("functional.interface.broken"));
          }
          ClassInheritorsSearch.search(superClass).forEach(sClass -> {
            if (!sClass.isInheritor(subclass, true) && !sClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
              for (PsiMethod aMethod : newAbstractMethods) {
                if (MethodSignatureUtil.findMethodBySignature(sClass, aMethod, true) == null) {
                  String description = RefactoringUIUtil.getDescription(sClass, true);
                  conflicts.putValue(sClass, JavaBundle.message("pull.up.concrete.inherit.abstract.method.conflict", description));
                }
              }
            }
            return true;
          });
        }
      }
    }
    RefactoringConflictsUtil.getInstance()
      .analyzeAccessibilityConflictsAfterMemberMove(movedMembers, superClass, VisibilityUtil.ESCALATE_VISIBILITY,
                                                    targetRepresentativeElement, abstrMethods, Conditions.alwaysTrue(), conflicts
      );
    if (superClass != null) {
      if (movedMembers2Super) {
        checkSuperclassMembers(superClass, infos, conflicts);
        if (isInterfaceTarget) {
          checkInterfaceTarget(infos, conflicts);
        }
      } else {
        final String qualifiedName = superClass.getQualifiedName();
        assert qualifiedName != null;
        if (superClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
          if (!Comparing.strEqual(StringUtil.getPackageName(qualifiedName), targetPackage.getQualifiedName())) {
            conflicts.putValue(superClass, JavaBundle
              .message("pull.up.accessible.conflict.1", RefactoringUIUtil.getDescription(superClass, true),
                       RefactoringUIUtil.getDescription(targetPackage, true)));
          }
        }
      }
    }
    // check if moved methods use other members in the classes between Subclass and Superclass
    List<PsiElement> checkModuleConflictsList = new ArrayList<>();
    for (PsiMember member : movedMembers) {
      if (member instanceof PsiMethod || member instanceof PsiClass && !(member instanceof PsiCompiledElement)) {
        ClassMemberReferencesVisitor visitor =
          movedMembers2Super? new ConflictingUsagesOfSubClassMembers(member, movedMembers, abstractMethods, subclass, superClass,
                                                 superClass != null ? null : targetPackage, conflicts,
                                                 interfaceContainmentVerifier)
                            : new ConflictingUsagesOfSuperClassMembers(member, subclass, targetPackage, movedMembers, conflicts);
        member.accept(visitor);
      }
      ContainerUtil.addIfNotNull(checkModuleConflictsList, member);
    }
    for (final PsiMethod method : abstractMethods) {
      ContainerUtil.addIfNotNull(checkModuleConflictsList, method.getParameterList());
      ContainerUtil.addIfNotNull(checkModuleConflictsList, method.getReturnTypeElement());
      ContainerUtil.addIfNotNull(checkModuleConflictsList, method.getTypeParameterList());
    }
    final VirtualFile vFile = PsiUtilCore.getVirtualFile(targetDirectory);
    if (vFile != null) {
      RefactoringConflictsUtil.getInstance()
        .analyzeModuleConflicts(subclass.getProject(), checkModuleConflictsList, UsageInfo.EMPTY_ARRAY, vFile, conflicts);
    }

    final PsiClassOwner psiFile = PsiTreeUtil.getParentOfType(subclass, PsiClassOwner.class);
    final boolean toDifferentPackage = !Comparing.strEqual(targetPackage.getQualifiedName(),
                                                           psiFile != null ? psiFile.getPackageName() : null);
    for (final PsiMethod abstractMethod : abstractMethods) {
      abstractMethod.accept(new ClassMemberReferencesVisitor(subclass) {
        @Override
        protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
          if (classMember != null && willBeMoved(classMember, movedMembers)) {
            boolean isAccessible = false;
            if (classMember.hasModifierProperty(PsiModifier.PRIVATE)) {
              isAccessible = true;
            }
            else if (classMember.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
                     toDifferentPackage) {
              isAccessible = true;
            }
            if (isAccessible) {
              String message = JavaRefactoringBundle
                .message("push.up.abstract.accessibility.in.subclass.conflict", RefactoringUIUtil.getDescription(abstractMethod, false),
                         RefactoringUIUtil.getDescription(classMember, true));
              message = StringUtil.capitalize(message);
              conflicts.putValue(classMember, message);
            }
          }
        }
      });
      if (abstractMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && toDifferentPackage) {
        if (!isInterfaceTarget) {
          String message = JavaRefactoringBundle
            .message("push.up.abstract.accessible.from.the.subclass.conflict", RefactoringUIUtil.getDescription(abstractMethod, false));
          message = StringUtil.capitalize(message);
          conflicts.putValue(abstractMethod, message);
        }
      }
    }
    return conflicts;
  }

  private static List<PsiMethod> newAbstractMethodInSuper(MemberInfoBase<? extends PsiMember>[] infos) {
    List<PsiMethod> result = new ArrayList<>();
    for (MemberInfoBase<? extends PsiMember> info : infos) {
      PsiMember member = info.getMember();
      if (member instanceof PsiMethod) {
        if (info.isToAbstract() || member.hasModifierProperty(PsiModifier.ABSTRACT)) {
          result.add((PsiMethod)member);
        }
      }
    }
    return result;
  }

  private static void checkInterfaceTarget(MemberInfoBase<? extends PsiMember>[] infos, MultiMap<PsiElement, String> conflictsList) {
    for (MemberInfoBase<? extends PsiMember> info : infos) {
      PsiModifierListOwner member = info.getMember();

      if (member instanceof PsiField || member instanceof PsiClass) {

        if (!member.hasModifierProperty(PsiModifier.STATIC)
            && !(member instanceof PsiClass && ((PsiClass)member).isInterface())) {
          String message =
            JavaRefactoringBundle.message("0.is.not.static.it.cannot.be.moved.to.the.interface", RefactoringUIUtil.getDescription(member, false));
          message = StringUtil.capitalize(message);
          conflictsList.putValue(member, message);
        }
      }

      if (member instanceof PsiField && ((PsiField)member).getInitializer() == null) {
        String message = JavaRefactoringBundle.message("0.is.not.initialized.in.declaration.such.fields.are.not.allowed.in.interfaces",
                                                   RefactoringUIUtil.getDescription(member, false));
        conflictsList.putValue(member, StringUtil.capitalize(message));
      }
    }
  }

  private static void checkSuperclassMembers(PsiClass superClass,
                                             MemberInfoBase<? extends PsiMember>[] infos,
                                             MultiMap<PsiElement, String> conflictsList) {
    for (MemberInfoBase<? extends PsiMember> info : infos) {
      PsiMember member = info.getMember();
      PsiMember superMember = null;
      if (member instanceof PsiField) {
        superMember = superClass.findFieldByName(member.getName(), false);
      }
      else if (member instanceof PsiMethod method) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) continue;
        PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
        MethodSignature signature = ((PsiMethod) member).getSignature(superSubstitutor);
        final PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySignature(superClass, signature, false);
        if (superClassMethod != null && !superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          superMember = superClassMethod;
        }
      }

      if (superMember != null) {
        String message = RefactoringBundle.message("0.already.contains.a.1",
                                                   RefactoringUIUtil.getDescription(superClass, false),
                                                   RefactoringUIUtil.getDescription(superMember, false));
        message = StringUtil.capitalize(message);
        conflictsList.putValue(superMember, message);
      }

      if (member instanceof PsiMethod method) {
        final PsiModifierList modifierList = method.getModifierList();
        if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
          for (PsiClass subClass : ClassInheritorsSearch.search(superClass)) {
            if (method.getContainingClass() != subClass) {
              MethodSignature signature =
                ((PsiMethod)member).getSignature(TypeConversionUtil.getSuperClassSubstitutor(superClass, subClass, PsiSubstitutor.EMPTY));
              final PsiMethod wouldBeOverriden = MethodSignatureUtil.findMethodBySignature(subClass, signature, false);
              if (wouldBeOverriden != null) {
                String modifier = VisibilityUtil.getVisibilityModifier(wouldBeOverriden.getModifierList());
                if (VisibilityUtil.compare(modifier, VisibilityUtil.getVisibilityModifier(modifierList)) > 0) {
                  String message = JavaRefactoringBundle.message("push.up.super.class.signature.conflict",
                                                                 RefactoringUIUtil.getDescription(method, true),
                                                                 RefactoringUIUtil.getDescription(subClass, true),
                                                                 modifier);
                  conflictsList.putValue(wouldBeOverriden, StringUtil.capitalize(message));
                }
              }
            }
          }
        }
      }
    }

  }

  private static boolean willBeMoved(PsiElement element, Set<PsiMember> movedMembers) {
    PsiElement parent = element;
    while (parent != null) {
      if (movedMembers.contains(parent)) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static class ConflictingUsagesOfSuperClassMembers extends ClassMemberReferencesVisitor {

    private final PsiMember myMember;
    private final PsiClass mySubClass;
    private final PsiPackage myTargetPackage;
    private final Set<PsiMember> myMovedMembers;
    private final MultiMap<PsiElement, String> myConflicts;

    ConflictingUsagesOfSuperClassMembers(PsiMember member, PsiClass aClass,
                                                PsiPackage targetPackage,
                                                Set<PsiMember> movedMembers,
                                                MultiMap<PsiElement, String> conflicts) {
      super(aClass);
      myMember = member;
      mySubClass = aClass;
      myTargetPackage = targetPackage;
      myMovedMembers = movedMembers;
      myConflicts = conflicts;
    }

    @Override
    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember != null && !willBeMoved(classMember, myMovedMembers)) {
        final PsiClass containingClass = classMember.getContainingClass();
        if (containingClass != null) {
          if (!PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage)) {
            if (classMember.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
              myConflicts.putValue(myMember, JavaBundle.message("pull.up.accessible.conflict", RefactoringUIUtil.getDescription(classMember, true)));
            }
            else if (classMember.hasModifierProperty(PsiModifier.PROTECTED) && !mySubClass.isInheritor(containingClass, true)) {
              myConflicts.putValue(myMember, JavaBundle.message("pull.up.accessible.conflict", RefactoringUIUtil.getDescription(classMember, true)));
            }
          }
        }
      }
    }
  }

  private static class ConflictingUsagesOfSubClassMembers extends ClassMemberReferencesVisitor {
    private final PsiElement myScope;
    private final Set<PsiMember> myMovedMembers;
    private final Set<PsiMethod> myAbstractMethods;
    private final PsiClass mySubclass;
    private final PsiClass mySuperClass;
    private final PsiPackage myTargetPackage;
    private final MultiMap<PsiElement, String> myConflictsList;
    private final InterfaceContainmentVerifier myInterfaceContainmentVerifier;

    ConflictingUsagesOfSubClassMembers(PsiElement scope,
                                       Set<PsiMember> movedMembers, Set<PsiMethod> abstractMethods,
                                       PsiClass subclass, PsiClass superClass,
                                       PsiPackage targetPackage, MultiMap<PsiElement, String> conflictsList,
                                       InterfaceContainmentVerifier interfaceContainmentVerifier) {
      super(subclass);
      myScope = scope;
      myMovedMembers = movedMembers;
      myAbstractMethods = abstractMethods;
      mySubclass = subclass;
      mySuperClass = superClass;
      myTargetPackage = targetPackage;
      myConflictsList = conflictsList;
      myInterfaceContainmentVerifier = interfaceContainmentVerifier;
    }

    @Override
    protected void visitClassMemberReferenceElement(PsiMember classMember,
                                                    PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember != null
          && RefactoringHierarchyUtil.isMemberBetween(mySuperClass, mySubclass, classMember)) {
        if (classMember.hasModifierProperty(PsiModifier.STATIC)
            && !willBeMoved(classMember, myMovedMembers)) {
          final boolean isAccessible;
          if (mySuperClass != null) {
            isAccessible = PsiUtil.isAccessible(classMember, mySuperClass, null);
          }
          else if (myTargetPackage != null) {
            isAccessible = PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage);
          }
          else {
            isAccessible = classMember.hasModifierProperty(PsiModifier.PUBLIC);
          }
          if (!isAccessible) {
            String message = RefactoringBundle.message("0.uses.1.which.is.not.accessible.from.the.superclass",
                                                       RefactoringUIUtil.getDescription(myScope, false),
                                                       RefactoringUIUtil.getDescription(classMember, true));
            message = StringUtil.capitalize(message);
            myConflictsList.putValue(classMember, message);

          }
          return;
        }
        if (!myAbstractMethods.contains(classMember) && !willBeMoved(classMember, myMovedMembers)) {
          if (!existsInSuperClass(classMember)) {
            String message = RefactoringBundle.message("0.uses.1.which.is.not.moved.to.the.superclass",
                                                       RefactoringUIUtil.getDescription(myScope, false),
                                                       RefactoringUIUtil.getDescription(classMember, true));
            message = StringUtil.capitalize(message);
            myConflictsList.putValue(classMember, message);
          }
        }
      }
    }



    private boolean existsInSuperClass(PsiElement classMember) {
      if (!(classMember instanceof PsiMethod method)) return false;
      if (myInterfaceContainmentVerifier.checkedInterfacesContain(method)) return true;
      if (mySuperClass == null) return false;
      final PsiMethod methodBySignature = mySuperClass.findMethodBySignature(method, true);
      return methodBySignature != null;
    }
  }


}
