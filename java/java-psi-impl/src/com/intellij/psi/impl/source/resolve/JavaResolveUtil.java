/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * @author max
 */
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaResolveUtil {
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

  public static PsiElement findParentContextOfClass(PsiElement element, Class aClass, boolean strict){
    PsiElement scope = strict ? element.getContext() : element;
    while(scope != null && !aClass.isInstance(scope)){
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
      }
      while (contextClass != null) {
        if (InheritanceUtil.isInheritorOrSelf(contextClass, memberClass, true)) {
          if (member instanceof PsiClass ||
              modifierList.hasModifierProperty(PsiModifier.STATIC) ||
              accessObjectClass == null ||
              InheritanceUtil.isInheritorOrSelf(accessObjectClass, contextClass, true)) {
            return true;
          }
        }

        contextClass = getContextClass(contextClass);
      }
      return false;
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
           ((FileResolveScopeProvider) placeFile).ignoreReferencedElementAccessibility() &&
           !PsiImplUtil.isInServerPage(placeFile);
  }

  public static boolean isInJavaDoc(final PsiElement place) {
    PsiElement scope = place;
    while(scope != null){
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

  public static void substituteResults(@NotNull final PsiJavaCodeReferenceElement ref, @NotNull JavaResolveResult[] result) {
    if (result.length > 0 && result[0].getElement() instanceof PsiClass) {
      for (int i = 0; i < result.length; i++) {
        final CandidateInfo resolveResult = (CandidateInfo)result[i];
        final PsiElement resultElement = resolveResult.getElement();
        if (resultElement instanceof PsiClass && ((PsiClass)resultElement).hasTypeParameters()) {
          PsiSubstitutor substitutor = resolveResult.getSubstitutor();
          result[i] = new CandidateInfo(resolveResult, substitutor) {
            @NotNull
            @Override
            public PsiSubstitutor getSubstitutor() {
              final PsiType[] parameters = ref.getTypeParameters();
              return super.getSubstitutor().putAll((PsiClass)resultElement, parameters);
            }
          };
        }
      }
    }
  }

  @NotNull
  public static <T extends PsiPolyVariantReference> JavaResolveResult[] resolveWithContainingFile(@NotNull T ref,
                                                                                                  @NotNull ResolveCache.PolyVariantContextResolver<T> resolver,
                                                                                                  boolean needToPreventRecursion,
                                                                                                  boolean incompleteCode,
                                                                                                  @NotNull PsiFile containingFile) {
    boolean valid = containingFile.isValid();
    if (!valid) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
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

    return PsiResolveHelper.SERVICE.getInstance(project)
      .resolveConstructor(PsiTypesUtil.getClassType(superClassWhichTheSuperCallMustResolveTo), expressionList, place).getElement();
  }
}