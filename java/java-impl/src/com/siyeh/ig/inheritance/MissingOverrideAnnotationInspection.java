// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.AnnotateMethodFix;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.module.JdkApiCompatabilityCache;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.JavaOverridingMethodUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class MissingOverrideAnnotationInspection extends BaseInspection implements CleanupLocalInspectionTool{
  @SuppressWarnings("PublicField")
  public boolean ignoreObjectMethods = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreAnonymousClassMethods;

  public boolean warnInSuper = true;

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "warnInSuper");
    writeBooleanOption(node, "warnInSuper", true);
  }

  @Override
  public @NotNull String getID() {
    return "override";
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final boolean annotateMethod = (boolean)infos[0];
    final boolean annotateHierarchy = (boolean)infos[1];
    return createAnnotateFix(annotateMethod, annotateHierarchy);
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final boolean annotateMethod = (boolean)infos[0];
    return InspectionGadgetsBundle.message(annotateMethod
                                           ? "missing.override.annotation.problem.descriptor"
                                           : "missing.override.annotation.in.overriding.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreObjectMethods", InspectionGadgetsBundle.message("ignore.equals.hashcode.and.tostring")),
      checkbox("ignoreAnonymousClassMethods", InspectionGadgetsBundle.message("ignore.methods.in.anonymous.classes")),
      checkbox("warnInSuper", InspectionGadgetsBundle.message("missing.override.warn.on.super.option")));
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ANNOTATIONS);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MissingOverrideAnnotationVisitor();
  }

  private class MissingOverrideAnnotationVisitor extends BaseInspectionVisitor {

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        if (method.getNameIdentifier() == null || method.isConstructor()) {
          return;
        }
        if (method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        final PsiClass methodClass = method.getContainingClass();
        if (methodClass == null) {
          return;
        }
        if (ignoreObjectMethods &&
            (MethodUtils.isHashCode(method) || MethodUtils.isEquals(method) || MethodUtils.isToString(method))) {
          return;
        }

        final boolean annotateMethod = isMissingOverride(method);
        final boolean annotateHierarchy = warnInSuper && isMissingOverrideInOverriders(method);
        if (annotateMethod || annotateHierarchy) {
          registerMethodError(method, annotateMethod, annotateHierarchy);
        }
      }

      // we assume:
      // 1) method name is not frequently used
      // 2) most of overridden methods already have @Override annotation
      // 3) only one annotation with short name 'Override' exists: it's 'java.lang.Override'
      private static boolean isMissingOverrideInOverriders(@NotNull PsiMethod method) {
        if (!PsiUtil.canBeOverridden(method)) return false;

        Project project = method.getProject();
        final boolean isInterface = Objects.requireNonNull(method.getContainingClass()).isInterface();
        JavaFeature requiredFeature = isInterface ? JavaFeature.OVERRIDE_INTERFACE : JavaFeature.ANNOTATIONS;

        GlobalSearchScope scope = getLanguageLevelScope(requiredFeature.getMinimumLevel(), project);
        if (scope == null) return false;
        int paramCount = method.getParameterList().getParametersCount();
        Predicate<PsiMethod> preFilter = m -> m.getParameterList().getParametersCount() == paramCount &&
                                              !JavaOverridingMethodUtil.containsAnnotationWithName(m, "Override");
        Stream<PsiMethod> overridingMethods = JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(method, scope, preFilter);
        return overridingMethods != null && overridingMethods.findAny().isPresent();
      }

      private boolean isMissingOverride(@NotNull PsiMethod method) {
        PsiClass methodClass = method.getContainingClass();
        if (ignoreAnonymousClassMethods && methodClass instanceof PsiAnonymousClass) {
          return false;
        }
        if (hasOverrideAnnotation(method)) {
          return false;
        }
        LanguageLevel level = PsiUtil.getLanguageLevel(method);
        if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
          return true;
        }
        if (JavaFeature.OVERRIDE_INTERFACE.isSufficient(level)) {
          if (!isJdk6Override(method, methodClass)) {
            return false;
          }
        }
        else if (!isJdk5Override(method, methodClass)) {
          return false;
        }
        return true;
      }

      private static boolean hasOverrideAnnotation(PsiModifierListOwner modifierListOwner) {
        final PsiModifierList modifierList = modifierListOwner.getModifierList();
        if (modifierList != null && modifierList.hasAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE)) {
          return true;
        }
        final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(modifierListOwner.getProject());
        final List<PsiAnnotation> annotations =
          annotationsManager.findExternalAnnotations(modifierListOwner, CommonClassNames.JAVA_LANG_OVERRIDE);
        return !annotations.isEmpty();
      }

      private static boolean isJdk6Override(PsiMethod method, PsiClass methodClass) {
        final PsiMethod[] superMethods = method.findSuperMethods();
        boolean hasSupers = false;
        for (PsiMethod superMethod : superMethods) {
          final PsiClass superClass = superMethod.getContainingClass();
          if (ignoreSuperMethod(method, methodClass, superMethod, superClass)) {
            continue;
          }
          hasSupers = true;
          if (!superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
            return true;
          }
        }
        // is override except if this is an interface method
        // overriding a protected method in java.lang.Object
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6501053
        return hasSupers && !methodClass.isInterface();
      }

      private static boolean isJdk5Override(PsiMethod method, PsiClass methodClass) {
        final PsiMethod[] superMethods = method.findSuperMethods();
        for (PsiMethod superMethod : superMethods) {
          final PsiClass superClass = superMethod.getContainingClass();
          if (ignoreSuperMethod(method, methodClass, superMethod, superClass)) {
            continue;
          }
          if (superClass.isInterface()) {
            continue;
          }
          if (methodClass.isInterface() &&
              superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
            // only true for J2SE java.lang.Object.clone(), but might
            // be different on other/newer java platforms
            continue;
          }
          return true;
        }
        return false;
      }

      @Contract("_, _, _,null -> true")
      private static boolean ignoreSuperMethod(PsiMethod method, PsiClass methodClass, PsiMethod superMethod, PsiClass superClass) {
        return !InheritanceUtil.isInheritorOrSelf(methodClass, superClass, true) ||
               JdkApiCompatabilityCache.getInstance().firstCompatibleLanguageLevel(superMethod, PsiUtil.getLanguageLevel(method)) != null;
      }
  }

  private static @NotNull LocalQuickFix createAnnotateFix(boolean annotateMethod, boolean annotateHierarchy) {
    return new AnnotateMethodFix(CommonClassNames.JAVA_LANG_OVERRIDE, annotateHierarchy, annotateMethod);
  }

  private static @Nullable GlobalSearchScope getLanguageLevelScope(@NotNull LanguageLevel _minimal, @NotNull Project project) {
    Map<LanguageLevel, GlobalSearchScope> map = CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Map<LanguageLevel, GlobalSearchScope> result = ConcurrentFactoryMap.createMap(minimal -> {
        Set<Module> modules = StreamEx
          .of(ModuleManager.getInstance(project).getModules())
          .filter(m -> LanguageLevelUtil.getEffectiveLanguageLevel(m).isAtLeast(minimal))
          .toSet();
        return modules == null ? null : new ModulesScope(modules, project);
      });
      return CachedValueProvider.Result.create(result, ProjectRootModificationTracker.getInstance(project));
    });
    return map.get(_minimal);
  }
}
