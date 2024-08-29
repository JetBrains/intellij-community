// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiJavaModuleModificationTracker;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.resolve.graphInference.PatternInference;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JavaResolveUtil {
  public static PsiClass getContextClass(@NotNull PsiElement element) {
    PsiElement prev = element;
    PsiElement scope = element.getContext();
    while (scope != null) {
      // skip the class if coming from its extends/implements list: those references only rely on the outer context for resolve
      if (scope instanceof PsiClass && (prev instanceof PsiMember || prev instanceof PsiDocComment)) {
        return (PsiClass)scope;
      }
      prev = scope;
      scope = scope.getContext();
    }
    return null;
  }

  public static PsiElement findParentContextOfClass(PsiElement element, Class<?> aClass, boolean strict) {
    PsiElement scope = strict ? element.getContext() : element;
    while (scope != null && !aClass.isInstance(scope)) {
      scope = scope.getContext();
    }
    return scope;
  }

  public static boolean isAccessible(@NotNull PsiMember member,
                                     @Nullable PsiClass memberClass,
                                     @Nullable PsiModifierList modifierList,
                                     @NotNull PsiElement place,
                                     @Nullable PsiClass accessObjectClass,
                                     @Nullable PsiElement fileResolveScope) {
    if (place instanceof PsiDirectory &&
        modifierList != null &&
        accessObjectClass == null &&
        PsiUtil.getAccessLevel(modifierList) == PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)place);
      return aPackage != null && JavaPsiFacade.getInstance(member.getProject()).isInPackage(member, aPackage);
    }
    return isAccessible(member, memberClass, modifierList, place, accessObjectClass, fileResolveScope, place.getContainingFile());
  }

  public static boolean isAccessible(@NotNull PsiMember member,
                                     @Nullable PsiClass memberClass,
                                     @Nullable PsiModifierList modifierList,
                                     @NotNull PsiElement place,
                                     @Nullable PsiClass accessObjectClass,
                                     @Nullable PsiElement fileResolveScope,
                                     @Nullable PsiFile placeFile) {
    if (modifierList == null || isInJavaDoc(place)) {
      return true;
    }

    if (placeFile instanceof JavaCodeFragment) {
      JavaCodeFragment fragment = (JavaCodeFragment)placeFile;
      JavaCodeFragment.VisibilityChecker visibilityChecker = fragment.getVisibilityChecker();
      if (visibilityChecker != null) {
        JavaCodeFragment.VisibilityChecker.Visibility visibility = visibilityChecker.isDeclarationVisible(member, place);
        if (visibility == JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE) return true;
        if (visibility == JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE) return false;
      }
    }
    else if (ignoreReferencedElementAccessibility(placeFile)) {
      return true;
    }

    if (accessObjectClass != null) {
      PsiClass containingClass = accessObjectClass.getContainingClass();
      if (!isAccessible(accessObjectClass, containingClass, accessObjectClass.getModifierList(), place, null, null, placeFile)) {
        return false;
      }
    }

    PsiFile file = placeFile == null ? null : FileContextUtil.getContextFile(placeFile); //TODO: implementation method!!!!
    if (PsiImplUtil.isInServerPage(file) && PsiImplUtil.isInServerPage(member.getContainingFile())) {
      return true;
    }

    int effectiveAccessLevel = PsiUtil.getAccessLevel(modifierList);
    if (ignoreReferencedElementAccessibility(file) || effectiveAccessLevel == PsiUtil.ACCESS_LEVEL_PUBLIC) {
      return true;
    }

    PsiManager manager = member.getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());

    if (effectiveAccessLevel == PsiUtil.ACCESS_LEVEL_PROTECTED) {
      if (facade.arePackagesTheSame(member, place)) {
        return true;
      }
      if (memberClass == null) {
        return false;
      }
      PsiClass contextClass;
      if (member instanceof PsiClass) {
        // if resolving supertype reference, skip its containing class with getContextClass
        contextClass = getContextClass(place);
      }
      else {
        contextClass = PsiTreeUtil.getContextOfType(place, PsiClass.class, false);
        if (isInClassAnnotationParameterList(place, contextClass)) return false;
        if (contextClass instanceof PsiAnonymousClass &&
            PsiTreeUtil.isAncestor(((PsiAnonymousClass)contextClass).getArgumentList(), place, true)) {
          contextClass = PsiTreeUtil.getContextOfType(contextClass, PsiClass.class, true);
        }
      }
      return canAccessProtectedMember(member, memberClass, accessObjectClass, contextClass,
                                      modifierList.hasModifierProperty(PsiModifier.STATIC));
    }

    if (effectiveAccessLevel == PsiUtil.ACCESS_LEVEL_PRIVATE) {
      if (memberClass == null) return true;
      if (accessObjectClass != null) {
        PsiClass topMemberClass = getTopLevelClass(memberClass, accessObjectClass);
        PsiClass topAccessClass = getTopLevelClass(accessObjectClass, memberClass);
        if (!manager.areElementsEquivalent(topMemberClass, topAccessClass)) return false;
        if (accessObjectClass instanceof PsiAnonymousClass && accessObjectClass.isInheritor(memberClass, true)) {
          if (!(place instanceof PsiAnonymousClass)) {
            return false;
          }
        }
      }

      PsiClass memberTopLevelClass = getTopLevelClass(memberClass, null);
      if (fileResolveScope == null) {
        PsiClass placeTopLevelClass = getTopLevelClass(place, null);
        return manager.areElementsEquivalent(placeTopLevelClass, memberTopLevelClass) &&
               !isInClassAnnotationParameterList(place, placeTopLevelClass);
      }
      else {
        PsiClass scopeTopLevelClass = getTopLevelClass(fileResolveScope, null);
        return manager.areElementsEquivalent(scopeTopLevelClass, memberTopLevelClass) &&
               fileResolveScope instanceof PsiClass &&
               !((PsiClass)fileResolveScope).isInheritor(memberClass, true);
      }
    }

    if (!facade.arePackagesTheSame(member, place)) return false;
    //if (modifierList.hasModifierProperty(PsiModifier.STATIC)) return true;
    // maybe inheritance lead through package-private class in other package ?
    final PsiClass placeClass = getContextClass(place);
    if (memberClass == null || placeClass == null) return true;
    // check only classes since interface members are public,  and if placeClass is interface,
    // then its members are static, and cannot refer to non-static members of memberClass
    if (memberClass.isInterface() || placeClass.isInterface()) return true;
    PsiClass clazz = accessObjectClass != null ?
                     accessObjectClass :
                     placeClass.getSuperClass(); //may start from super class
    if (clazz != null && clazz.isInheritor(memberClass, true)) {
      PsiClass superClass = clazz;
      while (!manager.areElementsEquivalent(superClass, memberClass)) {
        if (superClass == null || !facade.arePackagesTheSame(superClass, memberClass)) return false;
        superClass = superClass.getSuperClass();
      }
    }

    return true;
  }

  public static boolean canAccessProtectedMember(@NotNull PsiMember member,
                                                 @NotNull PsiClass memberClass,
                                                 @Nullable PsiClass accessObjectClass, @Nullable PsiClass contextClass, boolean isStatic) {
    while (contextClass != null) {
      if (InheritanceUtil.isInheritorOrSelf(contextClass, memberClass, true)) {
        if (member instanceof PsiClass || isStatic || accessObjectClass == null
            || InheritanceUtil.isInheritorOrSelf(accessObjectClass, contextClass, true)) {
          return true;
        }
      }

      contextClass = getContextClass(contextClass);
    }
    return false;
  }

  private static boolean isInClassAnnotationParameterList(@NotNull PsiElement place, @Nullable PsiClass contextClass) {
    if (contextClass != null) {
      PsiAnnotation annotation = PsiTreeUtil.getContextOfType(place, PsiAnnotation.class, true);
      if (annotation != null && PsiTreeUtil.isAncestor(contextClass.getModifierList(), annotation, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ignoreReferencedElementAccessibility(PsiFile placeFile) {
    return placeFile instanceof FileResolveScopeProvider &&
           ((FileResolveScopeProvider)placeFile).ignoreReferencedElementAccessibility() &&
           !PsiImplUtil.isInServerPage(placeFile);
  }

  public static boolean isInJavaDoc(@NotNull PsiElement place) {
    PsiElement scope = place;
    while (scope != null) {
      if (scope instanceof PsiDocComment) return true;
      if (scope instanceof PsiMember || scope instanceof PsiMethodCallExpression || scope instanceof PsiFile) return false;
      scope = scope.getContext();
    }
    return false;
  }

  private static PsiClass getTopLevelClass(@NotNull PsiElement place, PsiClass memberClass) {
    PsiClass lastClass = null;
    Boolean isAtLeast17 = null;
    for (PsiElement placeParent = place; placeParent != null; placeParent = placeParent.getContext()) {
      if (placeParent instanceof PsiClass && !(placeParent instanceof PsiAnonymousClass)) {
        final boolean isTypeParameter = placeParent instanceof PsiTypeParameter;
        if (isTypeParameter && isAtLeast17 == null) {
          isAtLeast17 = JavaVersionService.getInstance().isAtLeast(placeParent, JavaSdkVersion.JDK_1_7);
        }
        if (!isTypeParameter || isAtLeast17) {
          PsiClass aClass = (PsiClass)placeParent;

          if (memberClass != null && aClass.isInheritor(memberClass, true)) return aClass;

          lastClass = aClass;
        }
      }
    }

    return lastClass;
  }

  public static boolean processImplicitlyImportedPackages(final PsiScopeProcessor processor,
                                                          final ResolveState state,
                                                          final PsiElement place,
                                                          PsiManager manager) {
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage("");
    if (defaultPackage != null) {
      if (!defaultPackage.processDeclarations(processor, state, null, place)) return false;
    }

    PsiPackage langPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(CommonClassNames.DEFAULT_PACKAGE);
    if (langPackage != null) {
      if (!langPackage.processDeclarations(processor, state, null, place)) return false;
    }

    return true;
  }

  public static void substituteResults(final @NotNull PsiJavaCodeReferenceElement ref, JavaResolveResult @NotNull [] result) {
    if (result.length > 0 && result[0].getElement() instanceof PsiClass) {
      PsiDeconstructionPattern pattern = ObjectUtils.tryCast(ref.getParent().getParent(), PsiDeconstructionPattern.class);
      for (int i = 0; i < result.length; i++) {
        final CandidateInfo resolveResult = (CandidateInfo)result[i];
        final PsiElement resultElement = resolveResult.getElement();
        if (resultElement instanceof PsiClass) {
          PsiClass resultClass = (PsiClass)resultElement;
          if (resultClass.hasTypeParameters()) {
            PsiSubstitutor substitutor = resolveResult.getSubstitutor();
            result[i] = pattern != null && ref.getTypeParameterCount() == 0
                        ? PatternInference.inferPatternGenerics(resolveResult, pattern, resultClass,
                                                                JavaPsiPatternUtil.getContextType(pattern))
                        : new CandidateInfo(resolveResult, substitutor) {
                          @Override
                          public @NotNull PsiSubstitutor getSubstitutor() {
                            PsiType[] parameters = ref.getTypeParameters();
                            return super.getSubstitutor().putAll(resultClass, parameters);
                          }
                        };
          }
        }
      }
    }
  }

  public static <T extends PsiPolyVariantReference> JavaResolveResult @NotNull [] resolveWithContainingFile(@NotNull T ref,
                                                                                                            @NotNull ResolveCache.PolyVariantContextResolver<T> resolver,
                                                                                                            boolean needToPreventRecursion,
                                                                                                            boolean incompleteCode,
                                                                                                            @NotNull PsiFile containingFile) {
    Project project = containingFile.getProject();
    ResolveResult[] results = ResolveCache.getInstance(project).resolveWithCaching(ref, resolver, needToPreventRecursion, incompleteCode,
                                                                                   containingFile);
    return results.length == 0 ? JavaResolveResult.EMPTY_ARRAY : (JavaResolveResult[])results;
  }

  /**
   * @return the constructor (or a class if there are none)
   * which the "{@code super();}" no-args call resolves to if inserted in the {@code place} (typically it would be inserted in the sub class constructor)
   * No code modifications happen in this method; it's used for resolving multiple overloaded constructors.
   */
  public static PsiElement resolveImaginarySuperCallInThisPlace(@NotNull PsiMember place,
                                                                @NotNull Project project,
                                                                @NotNull PsiClass superClassWhichTheSuperCallMustResolveTo) {
    PsiExpressionListImpl expressionList = new PsiExpressionListImpl();
    final DummyHolder result = DummyHolderFactory.createHolder(PsiManager.getInstance(project), place);
    final FileElement holder = result.getTreeElement();
    holder.rawAddChildren((TreeElement)expressionList.getNode());

    return PsiResolveHelper.getInstance(project)
      .resolveConstructor(PsiTypesUtil.getClassType(superClassWhichTheSuperCallMustResolveTo), expressionList, place).getElement();
  }

  public static @Nullable PsiPackage getContainingPackage(final @NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file == null) return null;

    final PsiDirectory directory = file.getContainingDirectory();
    if (directory == null) return null;

    return JavaDirectoryService.getInstance().getPackage(directory);
  }

  public static boolean processJavaModuleExports(@NotNull PsiJavaModule module,
                                                 @NotNull PsiScopeProcessor processor,
                                                 @NotNull ResolveState state,
                                                 @Nullable PsiElement lastParent,
                                                 @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, module);
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) return true;

    List<PsiPackageAccessibilityStatement> exports = getExportedPackages(place, module);
    for (PsiPackageAccessibilityStatement export : exports) {
      PsiJavaCodeReferenceElement aPackage = export.getPackageReference();
      if (aPackage == null) continue;
      PsiElement resolvedPackage = aPackage.resolve();
      if (!(resolvedPackage instanceof PsiPackage)) continue;
      if (!resolvedPackage.processDeclarations(processor, state, lastParent, place)) return false;
    }
    return true;
  }

  /**
   * Retrieves a list of package accessibility statements for a given Java module that
   * are accessible to the specified place.
   *
   * @param place the place from which accessibility is being checked.
   * @param module the module whose exported packages are to be retrieved.
   * @return a list of `PsiPackageAccessibilityStatement` elements that represent the exported packages accessible
   *         to the specified place.
   */
  public static List<PsiPackageAccessibilityStatement> getExportedPackages(@NotNull PsiElement place, @NotNull PsiJavaModule module) {
    List<PsiPackageAccessibilityStatement> results = new ArrayList<>();
    PsiJavaModule currentModule = JavaModuleGraphHelper.getInstance().findDescriptorByElement(place);
    List<PsiPackageAccessibilityStatement> exports = getAllDeclaredExports(module);
    for (PsiPackageAccessibilityStatement export : exports) {
      PsiJavaCodeReferenceElement aPackage = export.getPackageReference();
      if (aPackage == null) continue;
      List<String> accessibleModules = export.getModuleNames();
      if (!accessibleModules.isEmpty()) {
        if (currentModule == null || !accessibleModules.contains(currentModule.getName())) continue;
      }
      results.add(export);
    }
    return results;
  }

  /**
   * Retrieves all transitive modules required by the given module, including the module itself.
   *
   * @param module the module for which transitive dependencies are being collected; must not be null
   * @return a set of transitive modules required by the given module, including the module itself
   */
  public static Set<PsiJavaModule> getAllTransitiveModulesIncludeCurrent(@NotNull PsiJavaModule module){
    return CachedValuesManager.getCachedValue(module, ()->{
      Project project = module.getProject();
      Set<PsiJavaModule> collected = new HashSet<>();
      collectAllTransitiveModulesIncludeCurrent(module, collected);
      return CachedValueProvider.Result.create(collected,
                                               PsiJavaModuleModificationTracker.getInstance(project),
                                               ProjectRootModificationTracker.getInstance(project));
    });
  }

  private static void collectAllTransitiveModulesIncludeCurrent(@NotNull PsiJavaModule module, @NotNull Set<PsiJavaModule> collected) {
    JavaModuleGraphHelper helper = JavaModuleGraphHelper.getInstance();
    Set<PsiJavaModule> dependencies = helper.getAllTransitiveDependencies(module);
    collected.addAll(dependencies);
    collected.add(module);
  }

  private static List<PsiPackageAccessibilityStatement> getAllDeclaredExports(@NotNull PsiJavaModule module) {
    Project project = module.getProject();
    return CachedValuesManager.getCachedValue(module, () -> {
      List<PsiPackageAccessibilityStatement> exports = new ArrayList<>();
      for (PsiJavaModule javaModule : getAllTransitiveModulesIncludeCurrent(module)) {
        for (PsiPackageAccessibilityStatement export : javaModule.getExports()) {
          exports.add(export);
        }
      }
      return CachedValueProvider.Result.create(exports,
                                        PsiJavaModuleModificationTracker.getInstance(project),
                                        ProjectRootModificationTracker.getInstance(project));
    });
  }
}