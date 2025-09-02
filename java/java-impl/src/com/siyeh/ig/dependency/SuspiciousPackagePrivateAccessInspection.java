// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dependency;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TestUtils;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.uast.*;

import java.util.*;

public final class SuspiciousPackagePrivateAccessInspection extends AbstractBaseUastLocalInspectionTool {
  @XCollection
  public List<ModulesSet> MODULES_SETS_LOADED_TOGETHER = new ArrayList<>();
  private final SynchronizedClearableLazy<Map<String, ModulesSet>> myModuleSetByModuleName = new SynchronizedClearableLazy<>(() -> {
    Map<String, ModulesSet> result = new HashMap<>();
    for (ModulesSet modulesSet : MODULES_SETS_LOADED_TOGETHER) {
      for (String module : modulesSet.modules) {
        result.put(module, modulesSet);
      }
    }
    return result;
  });

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    VirtualFile file = holder.getFile().getVirtualFile();
    if (file == null || !ProjectFileIndex.getInstance(holder.getProject()).isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return ApiUsageUastVisitor.createPsiElementVisitor(
      new SuspiciousApiUsageProcessor(holder, myModuleSetByModuleName.getValue())
    );
  }

  private static final class SuspiciousApiUsageProcessor implements ApiUsageProcessor {

    private final ProblemsHolder myProblemsHolder;
    private final Map<String, ModulesSet> myModuleNameToModulesSet;

    private SuspiciousApiUsageProcessor(ProblemsHolder problemsHolder, Map<String, ModulesSet> moduleNameToModulesSet) {
      myProblemsHolder = problemsHolder;
      myModuleNameToModulesSet = moduleNameToModulesSet;
    }

    @Override
    public void processReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target, @Nullable UExpression qualifier) {
      PsiClass accessObjectType = getAccessObjectType(qualifier);
      if (target instanceof PsiJvmMember) {
        checkAccess(sourceNode, (PsiJvmMember)target, accessObjectType);
        if (!(target instanceof PsiClass)) {
          if (accessObjectType != null) {
            checkAccess(sourceNode, accessObjectType, null);
          }
        }
      }
    }

    @Override
    public void processLambda(@NotNull ULambdaExpression sourceNode, @NotNull PsiModifierListOwner target) {
      processReference(sourceNode, target, null);
    }

    @Override
    public void processMethodOverriding(@NotNull UMethod method, @NotNull PsiMethod targetElement) {
      checkOverridePackageLocal(method, targetElement);
    }

    @Override
    public void processConstructorInvocation(@NotNull UElement sourceNode,
                                             @NotNull PsiClass instantiatedClass,
                                             @Nullable PsiMethod constructor,
                                             @Nullable UClass subclassDeclaration) {
      if (subclassDeclaration == null && constructor != null) {
        checkAccess(sourceNode, constructor, null);
      }
    }

    private static @Nullable PsiClass getAccessObjectType(@Nullable UExpression qualifier) {
      if (qualifier == null || qualifier instanceof UThisExpression || qualifier instanceof USuperExpression) {
        return null;
      }

      PsiType type = qualifier.getExpressionType();
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolve();
      }
      if (qualifier instanceof UReferenceExpression) {
        return ObjectUtils.tryCast(((UReferenceExpression)qualifier).resolve(), PsiClass.class);
      }
      return null;
    }

    private void checkAccess(@NotNull UElement sourceNode, @NotNull PsiJvmMember target, @Nullable PsiClass accessObjectType) {
      if (target.hasModifier(JvmModifier.PACKAGE_LOCAL)) {
        checkPackageLocalAccess(sourceNode, target, "package-private");
      }
      else if (target.hasModifier(JvmModifier.PROTECTED) && !canAccessProtectedMember(sourceNode, target, accessObjectType)) {
        checkPackageLocalAccess(sourceNode, target, "protected and used not through a subclass here");
      }
    }

    private void checkPackageLocalAccess(@NotNull UElement sourceNode, PsiJvmMember targetElement, final String accessType) {
      PsiElement sourcePsi = sourceNode.getSourcePsi();
      if (sourcePsi != null) {
        SuspiciousPackagePrivateAccess suspiciousAccess = verifyPackagePrivateAccess(sourcePsi, targetElement);
        if (suspiciousAccess != null &&
            PsiTreeUtil.getParentOfType(sourcePsi, PsiComment.class) == null) {
          List<IntentionAction> fixes =
            JvmElementActionFactories.createModifierActions(targetElement, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
          String elementDescription =
            StringUtil.removeHtmlTags(StringUtil.capitalize(RefactoringUIUtil.getDescription(targetElement, true)));
          LocalQuickFix[] quickFixes =
            IntentionWrapper.wrapToQuickFixes(fixes.toArray(IntentionAction.EMPTY_ARRAY), targetElement.getContainingFile());
          String message;
          if (suspiciousAccess.accessedFromTests) {
            message = InspectionGadgetsBundle.message("inspection.suspicious.package.private.access.from.tests.description", 
                                                      elementDescription, accessType);
          }
          else {
            message = InspectionGadgetsBundle.message("inspection.suspicious.package.private.access.description", elementDescription, 
                                                      accessType, suspiciousAccess.targetModule.getName());
          }
          myProblemsHolder.registerProblem(sourcePsi, message, quickFixes);
        }
      }
    }

    private void checkOverridePackageLocal(@NotNull UMethod sourceNode, @NotNull PsiJvmMember targetElement) {
      PsiElement sourcePsi = sourceNode.getSourcePsi();
      PsiElement nameIdentifier = UElementKt.getSourcePsiElement(sourceNode.getUastAnchor());
      if (sourcePsi != null && nameIdentifier != null && targetElement.hasModifier(JvmModifier.PACKAGE_LOCAL)) {
        SuspiciousPackagePrivateAccess accessResult = verifyPackagePrivateAccess(sourcePsi, targetElement);
        if (accessResult != null) {
          List<IntentionAction> fixes =
            JvmElementActionFactories.createModifierActions(targetElement, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
          String elementDescription =
            StringUtil.removeHtmlTags(StringUtil.capitalize(RefactoringUIUtil.getDescription(targetElement, false)));
          final String classDescription =
            StringUtil.removeHtmlTags(RefactoringUIUtil.getDescription(targetElement.getParent(), false));
          LocalQuickFix[] quickFixes =
            IntentionWrapper.wrapToQuickFixes(fixes.toArray(IntentionAction.EMPTY_ARRAY), targetElement.getContainingFile());
          String problem;
          if (accessResult.accessedFromTests) {
            problem = InspectionGadgetsBundle.message("inspection.suspicious.package.private.access.from.tests.problem", elementDescription,
                                                      classDescription);
          }
          else {
            problem = InspectionGadgetsBundle.message("inspection.suspicious.package.private.access.problem", elementDescription, 
                                                      classDescription, accessResult.targetModule.getName());
          }
          myProblemsHolder.registerProblem(nameIdentifier, problem, quickFixes);
        }
      }
    }

    private @Nullable SuspiciousPackagePrivateAccess verifyPackagePrivateAccess(@NotNull PsiElement sourceElement, @NotNull PsiElement targetElement) {
      Module targetModule = ModuleUtilCore.findModuleForPsiElement(targetElement);
      Module sourceModule = ModuleUtilCore.findModuleForPsiElement(sourceElement);
      if (targetModule == null || sourceModule == null) {
        return null;
      }
      if (targetModule.equals(sourceModule)) {
        if (TestUtils.isInTestSourceContent(sourceElement) && !TestUtils.isInTestSourceContent(targetElement)) {
          return new SuspiciousPackagePrivateAccess(targetModule, true);
        }
        return null;
      }
      ModulesSet sourceGroup = myModuleNameToModulesSet.get(sourceModule.getName());
      ModulesSet targetGroup = myModuleNameToModulesSet.get(targetModule.getName());
      if (sourceGroup == null || sourceGroup != targetGroup) {
        return new SuspiciousPackagePrivateAccess(targetModule, false);
      }
      return null;
    }
    
    private record SuspiciousPackagePrivateAccess(Module targetModule, boolean accessedFromTests) {}
  }

  private static boolean canAccessProtectedMember(UElement sourceNode, PsiMember member, PsiClass accessObjectType) {
    PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) return false;

    PsiClass contextClass = getContextClass(sourceNode, member instanceof PsiClass);
    if (contextClass == null) return false;

    return canAccessProtectedMember(member, memberClass, accessObjectType, member.hasModifierProperty(PsiModifier.STATIC), contextClass);
  }

  /**
   * Returns {@code true} if protected {@code member} can be accessed from {@code contextClass} at runtime if code compiles successfully.
   */
  private static boolean canAccessProtectedMember(@NotNull PsiMember member, @NotNull PsiClass memberClass,
                                                  @Nullable PsiClass accessObjectClass, boolean isStatic, @NotNull PsiClass contextClass) {
    if (!ClassUtils.inSamePackage(memberClass, contextClass)) {
      //in this case if the code compiles ok javac will generate required bridge methods so there will be no problems at runtime
      return true;
    }

    /*
     since classes are located in the same package javac won't generate bridge methods for members inherited by enclosing class, so
     local and inner classes won't have access to protected methods inherited by enclosing class, and therefore we shouldn't check
     enclosing classes here like JavaResolveUtil.canAccessProtectedMember does.
    */
    if (InheritanceUtil.isInheritorOrSelf(contextClass, memberClass, true)) {
      if (member instanceof PsiClass || isStatic || accessObjectClass == null
          || InheritanceUtil.isInheritorOrSelf(accessObjectClass, contextClass, true)) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable PsiClass getContextClass(@NotNull UElement sourceNode, boolean forClassReference) {
    PsiElement sourcePsi = sourceNode.getSourcePsi();
    UClass sourceClass = UastUtils.findContaining(sourcePsi, UClass.class);
    if (sourceClass == null) return null;
    if (isReferenceBelongsToEnclosingClass(sourceNode, sourceClass, forClassReference)) {
      UClass parentClass = UastUtils.getContainingUClass(sourceClass);
      return parentClass != null ? parentClass.getJavaPsi() : null;
    }
    return sourceClass.getJavaPsi();
  }

  private static boolean isReferenceBelongsToEnclosingClass(@NotNull UElement sourceNode, @NotNull UClass sourceClass,
                                                            boolean forClassReference) {
    UElement parent = sourceClass.getUastParent();
    if (parent instanceof UObjectLiteralExpression) {
      if (ContainerUtil.exists(((UCallExpression)parent).getValueArguments(), it -> UastUtils.isPsiAncestor(it, sourceNode))) {
        return true;
      }
    }
    return forClassReference && ContainerUtil.exists(sourceClass.getUastSuperTypes(), it -> UastUtils.isPsiAncestor(it, sourceNode));
  }

  @Tag("modules-set")
  public static class ModulesSet {
    @XCollection(elementName = "module", valueAttributeName = "name")
    @Property(surroundWithTag = false)
    public Set<String> modules = new LinkedHashSet<>();
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController()
      .onValue("MODULES_SETS_LOADED_TOGETHER",
               () -> StreamEx.of(MODULES_SETS_LOADED_TOGETHER).map(set -> String.join(",", set.modules)).toMutableList(),
               newList -> {
                 MODULES_SETS_LOADED_TOGETHER.clear();
                 StreamEx.of(newList).map(line -> {
                   ModulesSet set = new ModulesSet();
                   set.modules = StreamEx.split(line, ",").toCollection(LinkedHashSet::new);
                   return set;
                 }).into(MODULES_SETS_LOADED_TOGETHER);
                 myModuleSetByModuleName.drop();
               });
  }

  @Override
  public void readSettings(@NotNull Element node) {
    super.readSettings(node);
    myModuleSetByModuleName.drop();
  }
}
