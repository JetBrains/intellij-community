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
package com.intellij.codeInspection.visibility;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.fixes.ChangeModifierFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class AccessCanBeTightenedInspection extends BaseJavaBatchLocalInspectionTool {
  private final VisibilityInspection myVisibilityInspection;

  AccessCanBeTightenedInspection(@NotNull VisibilityInspection visibilityInspection) {
    myVisibilityInspection = visibilityInspection;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.VISIBILITY_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Member access can be tightened";
  }

  @Override
  @NotNull
  public String getShortName() {
    return VisibilityInspection.SHORT_NAME;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new MyVisitor(holder);
  }

  private class MyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final UnusedDeclarationInspectionBase myDeadCodeInspection;

    public MyVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
      InspectionProfile profile = InspectionProjectProfileManager.getInstance(holder.getProject()).getInspectionProfile();
      UnusedDeclarationInspectionBase tool = (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedDeclarationInspectionBase.SHORT_NAME, holder.getFile());
      myDeadCodeInspection = tool == null ? new UnusedDeclarationInspectionBase() : tool;
    }
    private final Set<PsiClass> childMembersAreUsedOutsideMyPackage = ContainerUtil.newConcurrentSet();

    @Override
    public void visitClass(PsiClass aClass) {
      checkMember(aClass);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      checkMember(method);
    }

    @Override
    public void visitField(PsiField field) {
      checkMember(field);
    }

    private void checkMember(@NotNull final PsiMember member) {
      if (member.hasModifierProperty(PsiModifier.PRIVATE) || member.hasModifierProperty(PsiModifier.NATIVE)) return;
      if (member instanceof PsiMethod && member instanceof SyntheticElement  || !member.isPhysical()) return;

      if (member instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)member;
        if (!method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) {
          log(member.getName() + " overrides");
          return; // overrides
        }
        if (MethodUtils.isOverridden(method)) {
          log(member.getName() + " overridden");
          return;
        }
      }
      if (member instanceof PsiEnumConstant) return;
      if (member instanceof PsiClass && (member instanceof PsiAnonymousClass ||
                                         member instanceof PsiTypeParameter ||
                                         member instanceof PsiSyntheticClass ||
                                         PsiUtil.isLocalClass((PsiClass)member))) {
        return;
      }
      final PsiClass memberClass = member.getContainingClass();
      if (memberClass != null && (memberClass.isInterface() || memberClass.isEnum() || memberClass.isAnnotationType() || PsiUtil.isLocalClass(memberClass) && member instanceof PsiClass)) {
        return;
      }
      final PsiFile memberFile = member.getContainingFile();
      Project project = memberFile.getProject();

      if (myDeadCodeInspection.isEntryPoint(member)) {
        log(member.getName() +" is entry point");
        return;
      }

      PsiModifierList memberModifierList = member.getModifierList();
      if (memberModifierList == null) return;
      final int currentLevel = PsiUtil.getAccessLevel(memberModifierList);
      final AtomicInteger maxLevel = new AtomicInteger(PsiUtil.ACCESS_LEVEL_PRIVATE);
      final AtomicBoolean foundUsage = new AtomicBoolean();
      PsiDirectory memberDirectory = memberFile.getContainingDirectory();
      final PsiPackage memberPackage = memberDirectory == null ? null : JavaDirectoryService.getInstance().getPackage(memberDirectory);
      log(member.getName()+ ": checking effective level for "+member);
      boolean result =
        UnusedSymbolUtil.processUsages(project, memberFile, member, new EmptyProgressIndicator(), null, info -> {
          foundUsage.set(true);
          PsiFile psiFile = info.getFile();
          if (psiFile == null) return true;
          if (!(psiFile instanceof PsiJavaFile)) {
            log("     refd from " + psiFile.getName() + "; set to public");
            maxLevel.set(PsiUtil.ACCESS_LEVEL_PUBLIC);
            if (memberClass != null) {
              childMembersAreUsedOutsideMyPackage.add(memberClass);
            }
            return false; // referenced from XML, has to be public
          }
          //int offset = info.getNavigationOffset();
          //if (offset == -1) return true;
          PsiElement element = info.getElement();
          if (element == null) return true;
          @PsiUtil.AccessLevel
          int level = getEffectiveLevel(element, psiFile, member, memberFile, memberClass, memberPackage);
          log("    ref in file " + psiFile.getName() + "; level = " + PsiUtil.getAccessModifier(level) + "; (" + element + ")");
          maxLevel.getAndAccumulate(level, Math::max);
          if (level == PsiUtil.ACCESS_LEVEL_PUBLIC && memberClass != null) {
            childMembersAreUsedOutsideMyPackage.add(memberClass);
          }

          return level != PsiUtil.ACCESS_LEVEL_PUBLIC;
        });

      if (!foundUsage.get()) {
        log(member.getName() + " unused; ignore");
        return; // do not propose private for unused method
      }

      int max = maxLevel.get();
      if (max == PsiUtil.ACCESS_LEVEL_PRIVATE && memberClass == null) {
        max = suggestPackageLocal(member);
      }

      String maxModifier = PsiUtil.getAccessModifier(max);
      log(member.getName() + ": effective level is '" + maxModifier + "'");

      if (max < currentLevel) {
        if (max == PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL && member instanceof PsiClass && childMembersAreUsedOutsideMyPackage.contains(member)) {
          log(member.getName() + "  children used outside my package; ignore");
          return; // e.g. some public method is used outside my package (without importing class)
        }
        PsiElement toHighlight = currentLevel == PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL ? ((PsiNameIdentifierOwner)member).getNameIdentifier() : ContainerUtil.find(
          memberModifierList.getChildren(),
          element -> element instanceof PsiKeyword && element.getText().equals(PsiUtil.getAccessModifier(currentLevel)));
        assert toHighlight != null : member +" ; " + ((PsiNameIdentifierOwner)member).getNameIdentifier() + "; "+ memberModifierList.getText();
        myHolder.registerProblem(toHighlight, "Access can be " + VisibilityUtil.toPresentableText(maxModifier), new ChangeModifierFix(maxModifier));
      }
    }

    @PsiUtil.AccessLevel
    private int getEffectiveLevel(@NotNull PsiElement element,
                                  @NotNull PsiFile file,
                                  @NotNull PsiMember member,
                                  @NotNull PsiFile memberFile,
                                  PsiClass memberClass,
                                  PsiPackage memberPackage) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      boolean isAbstractMember = member.hasModifierProperty(PsiModifier.ABSTRACT);
      if (memberClass != null && PsiTreeUtil.isAncestor(aClass, memberClass, false) ||
          aClass != null && PsiTreeUtil.isAncestor(memberClass, aClass, false)) {
        // access from the same file can be via private
        // except when used in annotation:
        // @Ann(value = C.VAL) class C { public static final String VAL = "xx"; }
        // or in implements/extends clauses
        if (isInReferenceList(aClass.getModifierList(), member) ||
            isInReferenceList(aClass.getImplementsList(), member) ||
            isInReferenceList(aClass.getExtendsList(), member)) {
          return suggestPackageLocal(member);
        }

        return !isAbstractMember && (myVisibilityInspection.SUGGEST_PRIVATE_FOR_INNERS ||
               !isInnerClass(memberClass)) ? PsiUtil.ACCESS_LEVEL_PRIVATE : suggestPackageLocal(member);
      }
      //if (file == memberFile) {
      //  return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
      //}
      PsiDirectory directory = file.getContainingDirectory();
      PsiPackage aPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
      if (aPackage == memberPackage || aPackage != null && memberPackage != null && Comparing.strEqual(aPackage.getQualifiedName(), memberPackage.getQualifiedName())) {
        return suggestPackageLocal(element);
      }
      if (aClass != null && memberClass != null && aClass.isInheritor(memberClass, true)) {
        //access from subclass can be via protected, except for constructors
        PsiElement resolved = element instanceof PsiReference ? ((PsiReference)element).resolve() : null;
        boolean isConstructor = resolved instanceof PsiClass && element.getParent() instanceof PsiNewExpression
                                || resolved instanceof PsiMethod && ((PsiMethod)resolved).isConstructor();
        if (!isConstructor) {
          return PsiUtil.ACCESS_LEVEL_PROTECTED;
        }
      }
      return PsiUtil.ACCESS_LEVEL_PUBLIC;
    }
  }

  private static boolean isInnerClass(@NotNull PsiClass memberClass) {
    return memberClass.getContainingClass() != null || memberClass instanceof PsiAnonymousClass;
  }

  private static boolean isInReferenceList(@Nullable PsiElement list, @NotNull final PsiMember member) {
    if (list == null) return false;
    final PsiManager psiManager = member.getManager();
    final boolean[] result = new boolean[1];
    list.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        if (psiManager.areElementsEquivalent(reference.resolve(), member)) {
          result[0] = true;
          stopWalking();
        }
      }
    });
    return result[0];
  }

  private int suggestPackageLocal(@NotNull PsiElement member) {
    boolean suggestPackageLocal = member instanceof PsiClass && ClassUtil.isTopLevelClass((PsiClass)member)
                ? myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES
                : myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS;
    return suggestPackageLocal ? PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL : PsiUtil.ACCESS_LEVEL_PUBLIC;
  }

  private static void log(String s) {
    //System.out.println(s);
  }
}
