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
import com.intellij.openapi.util.Condition;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.fixes.ChangeModifierFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AccessCanBeTightenedInspection extends BaseJavaBatchLocalInspectionTool {
  @NonNls private static final String SHORT_NAME = "AccessCanBeTightened";

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
    return SHORT_NAME;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new MyVisitor(holder);
  }

  private static class MyVisitor extends JavaElementVisitor {
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

    private static boolean isOverridden(PsiMethod method) {
      if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return false;
      }
      final Query<PsiMethod> overridingMethodQuery = OverridingMethodsSearch.search(method);
      final PsiMethod result = overridingMethodQuery.findFirst();
      return result != null;
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
        if (isOverridden(method)) {
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
        UnusedSymbolUtil.processUsages(project, memberFile, member, new EmptyProgressIndicator(), null, new Processor<UsageInfo>() {
          @Override
          public boolean process(UsageInfo info) {
            if (member.getName().equals("getConstBoolean")) {
              int i = 0;
            }
            foundUsage.set(true);
            PsiFile psiFile = info.getFile();
            if (psiFile == null) return true; // ignore usages in containingFile because isLocallyUsed() method would have caught that
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
            int level = getEffectiveLevel(element, psiFile, memberFile, memberClass, memberPackage);
            log("    ref in file " + psiFile.getName() + "; level = " + PsiUtil.getAccessModifier(level) + "; (" + element + ")");
            int oldLevel = maxLevel.get();
            if (level > oldLevel) {
              boolean set = maxLevel.compareAndSet(oldLevel, level);
              //assert set;
            }
            if (level == PsiUtil.ACCESS_LEVEL_PUBLIC && memberClass != null) {
              childMembersAreUsedOutsideMyPackage.add(memberClass);
            }

            return level != PsiUtil.ACCESS_LEVEL_PUBLIC;
          }
        });

      if (!foundUsage.get()) {
        log(member.getName() + " unused; ignore");
        return; // do not propose private for unused method
      }
      int max = maxLevel.get();
      if (max == PsiUtil.ACCESS_LEVEL_PRIVATE && memberClass == null) {
        max = PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
      }

      log(member.getName()+": effective level is '" + PsiUtil.getAccessModifier(max) + "'");

      if (max < currentLevel) {
        if (max == PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL && member instanceof PsiClass && childMembersAreUsedOutsideMyPackage.contains(member)) {
          log(member.getName() + "  children used outside my package; ignore");
          return; // e.g. some public method is used outside my package (without importing class)
        }
        PsiElement toHighlight = currentLevel == PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL ? ((PsiNameIdentifierOwner)member).getNameIdentifier() : ContainerUtil.find(
          memberModifierList.getChildren(), new Condition<PsiElement>() {
            @Override
            public boolean value(PsiElement element) {
              return element instanceof PsiKeyword && element.getText().equals(PsiUtil.getAccessModifier(currentLevel));
            }
          });
        assert toHighlight != null : member +" ; " + ((PsiNameIdentifierOwner)member).getNameIdentifier() + "; "+ memberModifierList.getText();
        myHolder.registerProblem(toHighlight, "Access can be "+PsiUtil.getAccessModifier(max), new ChangeModifierFix(PsiUtil.getAccessModifier(max)));
      }
    }

    @PsiUtil.AccessLevel
    private static int getEffectiveLevel(@NotNull PsiElement element,
                                         @NotNull PsiFile file,
                                         @NotNull PsiFile memberFile,
                                         PsiClass memberClass,
                                         PsiPackage memberPackage) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (memberClass != null && PsiTreeUtil.isAncestor(aClass, memberClass, false) ||
          aClass != null && PsiTreeUtil.isAncestor(memberClass, aClass, false)) {
        // access from the same file can be via private
        // except when used in annotation:
        // @Ann(value = C.VAL) class C { public static final String VAL = "xx"; }
        PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
        if (annotation != null && annotation.getParent() instanceof PsiModifierList && annotation.getParent().getParent() == aClass) {
          return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
        }
        return PsiUtil.ACCESS_LEVEL_PRIVATE;
      }
      //if (file == memberFile) {
      //  return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
      //}
      PsiDirectory directory = file.getContainingDirectory();
      PsiPackage aPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
      if (aPackage == memberPackage || aPackage != null && memberPackage != null && Comparing.strEqual(aPackage.getQualifiedName(), memberPackage.getQualifiedName())) {
        return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
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

  private static void log(String s) {
    //System.out.println(s);
  }
}
