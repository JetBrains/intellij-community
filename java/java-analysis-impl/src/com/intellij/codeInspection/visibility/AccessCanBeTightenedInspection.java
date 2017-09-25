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
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.fixes.ChangeModifierFix;
import com.siyeh.ig.psiutils.MethodUtils;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      myDeadCodeInspection = UnusedDeclarationInspectionBase.findUnusedDeclarationInspection(holder.getFile());
    }
    private final TObjectIntHashMap<PsiClass> maxSuggestedLevelForChildMembers = new TObjectIntHashMap<>();

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
      final PsiClass memberClass = member.getContainingClass();
      PsiModifierList memberModifierList = member.getModifierList();
      if (memberModifierList == null) return;
      int currentLevel = PsiUtil.getAccessLevel(memberModifierList);
      int suggestedLevel = suggestLevel(member, memberClass, currentLevel);
      if (memberClass != null) {
        synchronized (maxSuggestedLevelForChildMembers) {
          int prevMax = maxSuggestedLevelForChildMembers.get(memberClass);
          maxSuggestedLevelForChildMembers.put(memberClass, Math.max(prevMax, suggestedLevel));
        }
      }

      log(member.getName() + ": effective level is '" + PsiUtil.getAccessModifier(suggestedLevel) + "'");

      if (suggestedLevel < currentLevel) {
        if (member instanceof PsiClass) {
          int memberMaxLevel;
          synchronized (maxSuggestedLevelForChildMembers) {
            memberMaxLevel = maxSuggestedLevelForChildMembers.get((PsiClass)member);
          }
          if (memberMaxLevel > suggestedLevel) {
            // a class can't have visibility less than its members
            return;
          }
        }
        PsiElement toHighlight = currentLevel == PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL ? ((PsiNameIdentifierOwner)member).getNameIdentifier() :
                                 ContainerUtil.find(memberModifierList.getChildren(),
          element -> element instanceof PsiKeyword && element.getText().equals(PsiUtil.getAccessModifier(currentLevel)));
        // can be null in some strange cases of malbuilt PSI, like in EA-95877
        if (toHighlight != null) {
          String suggestedModifier = PsiUtil.getAccessModifier(suggestedLevel);
          myHolder.registerProblem(toHighlight, "Access can be " + VisibilityUtil.toPresentableText(suggestedModifier), new ChangeModifierFix(suggestedModifier));
        }
      }
    }

    @PsiUtil.AccessLevel
    private int suggestLevel(@NotNull PsiMember member, PsiClass memberClass, @PsiUtil.AccessLevel int currentLevel) {
      if (member.hasModifierProperty(PsiModifier.PRIVATE) || member.hasModifierProperty(PsiModifier.NATIVE)) return currentLevel;
      if (member instanceof PsiMethod && member instanceof SyntheticElement || !member.isPhysical()) return currentLevel;

      if (member instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)member;
        if (!method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) {
          log(member.getName() + " overrides");
          return currentLevel; // overrides
        }
        if (MethodUtils.isOverridden(method)) {
          log(member.getName() + " overridden");
          return currentLevel;
        }
      }
      if (member instanceof PsiEnumConstant) return currentLevel;
      if (member instanceof PsiClass && (member instanceof PsiAnonymousClass ||
                                         member instanceof PsiTypeParameter ||
                                         member instanceof PsiSyntheticClass ||
                                         PsiUtil.isLocalClass((PsiClass)member))) {
        return currentLevel;
      }
      if (memberClass != null && (memberClass.isInterface() || memberClass.isEnum() || memberClass.isAnnotationType() || PsiUtil.isLocalClass(memberClass) && member instanceof PsiClass)) {
        return currentLevel;
      }
      final PsiFile memberFile = member.getContainingFile();
      Project project = memberFile.getProject();

      int minLevel = PsiUtil.ACCESS_LEVEL_PRIVATE;
      boolean entryPoint = myDeadCodeInspection.isEntryPoint(member);
      if (entryPoint) {
        int level = myVisibilityInspection.getMinVisibilityLevel(member);
        if (level <= 0) {
          log(member.getName() +" is entry point");
          return currentLevel;
        }
        else {
          minLevel = level;
        }
      }

      PsiDirectory memberDirectory = memberFile.getContainingDirectory();
      final PsiPackage memberPackage = memberDirectory == null ? null : JavaDirectoryService.getInstance().getPackage(memberDirectory);
      log(member.getName()+ ": checking effective level for "+member);

      AtomicInteger maxLevel = new AtomicInteger(minLevel);
      AtomicBoolean foundUsage = new AtomicBoolean();
      boolean proceed = UnusedSymbolUtil.processUsages(project, memberFile, member, new EmptyProgressIndicator(), null, info -> {
        PsiElement element = info.getElement();
        if (element == null) return true;
        PsiFile psiFile = info.getFile();
        if (psiFile == null) return true;

        return handleUsage(member, memberClass, memberFile, maxLevel, memberPackage, element, psiFile, foundUsage);
      });

      if (proceed && member instanceof PsiClass && LambdaUtil.isFunctionalClass((PsiClass)member)) {
        // there can be lambda implementing this interface implicitly
        FunctionalExpressionSearch.search((PsiClass)member).forEach(functionalExpression -> {
          PsiFile psiFile = functionalExpression.getContainingFile();
          return handleUsage(member, memberClass, memberFile, maxLevel, memberPackage, functionalExpression, psiFile, foundUsage);
        });
      }
      if (!foundUsage.get() && !entryPoint) {
        log(member.getName() + " unused; ignore");
        return currentLevel; // do not propose private for unused method
      }

      @PsiUtil.AccessLevel
      int suggestedLevel = maxLevel.get();
      if (suggestedLevel == PsiUtil.ACCESS_LEVEL_PRIVATE && memberClass == null) {
        suggestedLevel = suggestPackageLocal(member);
      }

      String suggestedModifier = PsiUtil.getAccessModifier(suggestedLevel);
      log(member.getName() + ": effective level is '" + suggestedModifier + "'");

      return suggestedLevel;
    }


    private boolean handleUsage(@NotNull PsiMember member,
                                @Nullable PsiClass memberClass,
                                @NotNull PsiFile memberFile,
                                @NotNull AtomicInteger maxLevel,
                                @Nullable PsiPackage memberPackage,
                                @NotNull PsiElement element,
                                @NotNull PsiFile psiFile,
                                @NotNull AtomicBoolean foundUsage) {
      foundUsage.set(true);
      if (!(psiFile instanceof PsiJavaFile)) {
        log("     refd from " + psiFile.getName() + "; set to public");
        maxLevel.set(PsiUtil.ACCESS_LEVEL_PUBLIC);
        return false; // referenced from XML, has to be public
      }
      @PsiUtil.AccessLevel
      int level = getEffectiveLevel(element, psiFile, member, memberFile, memberClass, memberPackage);
      log("    ref in file " + psiFile.getName() + "; level = " + PsiUtil.getAccessModifier(level) + "; (" + element + ")");
      maxLevel.getAndAccumulate(level, Math::max);

      return level != PsiUtil.ACCESS_LEVEL_PUBLIC;
    }

    @PsiUtil.AccessLevel
    private int getEffectiveLevel(@NotNull PsiElement element,
                                  @NotNull PsiFile file,
                                  @NotNull PsiMember member,
                                  @NotNull PsiFile memberFile,
                                  PsiClass memberClass,
                                  PsiPackage memberPackage) {
      PsiClass innerClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      boolean isAbstractMember = member.hasModifierProperty(PsiModifier.ABSTRACT);
      if (memberClass != null && PsiTreeUtil.isAncestor(innerClass, memberClass, false) ||
          innerClass != null && PsiTreeUtil.isAncestor(memberClass, innerClass, false) && !innerClass.hasModifierProperty(PsiModifier.STATIC)) {
        // access from the same file can be via private
        // except when used in annotation:
        // @Ann(value = C.VAL) class C { public static final String VAL = "xx"; }
        // or in implements/extends clauses
        if (isInReferenceList(innerClass.getModifierList(), member) ||
            isInReferenceList(innerClass.getImplementsList(), member) ||
            isInReferenceList(innerClass.getExtendsList(), member)) {
          return suggestPackageLocal(member);
        }

        return !isAbstractMember && (myVisibilityInspection.SUGGEST_PRIVATE_FOR_INNERS ||
               !isInnerClass(memberClass)) ? PsiUtil.ACCESS_LEVEL_PRIVATE : suggestPackageLocal(member);
      }
      //if (file == memberFile) {
      //  return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
      //}
      PsiExpression qualifier = null;
      if (element instanceof PsiReferenceExpression) {
        qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
      }
      else if (element instanceof PsiMethodCallExpression) {
        qualifier = ((PsiMethodCallExpression)element).getMethodExpression().getQualifierExpression();
      }

      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        return PsiUtil.ACCESS_LEVEL_PUBLIC;
      }
      PsiDirectory directory = file.getContainingDirectory();
      PsiPackage aPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
      if (aPackage == memberPackage || aPackage != null && memberPackage != null && Comparing.strEqual(aPackage.getQualifiedName(), memberPackage.getQualifiedName())) {
        return suggestPackageLocal(member);
      }
      if (innerClass != null && memberClass != null && innerClass.isInheritor(memberClass, true)) {
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

  @PsiUtil.AccessLevel
  private int suggestPackageLocal(@NotNull PsiMember member) {
    boolean suggestPackageLocal = member instanceof PsiClass && ClassUtil.isTopLevelClass((PsiClass)member)
                ? myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES
                : myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS;
    return suggestPackageLocal ? PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL : PsiUtil.ACCESS_LEVEL_PUBLIC;
  }

  private static void log(String s) {
    //System.out.println(s);
  }
}
