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

/*
 * @author max
 */
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaResolveUtil {
  public static PsiClass getContextClass(PsiElement element) {
    PsiElement scope = element.getContext();
    while (scope != null) {
      if (scope instanceof PsiClass) return (PsiClass)scope;
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
      for (PsiElement placeParent = place; placeParent != null; placeParent = placeParent.getContext()) {
        if (placeParent instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)placeParent, memberClass, true)) {
          if (member instanceof PsiClass ||
              modifierList.hasModifierProperty(PsiModifier.STATIC) ||
              accessObjectClass == null ||
              InheritanceUtil.isInheritorOrSelf(accessObjectClass, (PsiClass)placeParent, true)) {
            return true;
          }
        }
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
          if (place instanceof PsiMethodCallExpression) {
            return false;
          }
        }
      }

      if (fileResolveScope == null) {
        PsiClass placeTopLevelClass = getTopLevelClass(place, null);
        PsiClass memberTopLevelClass = getTopLevelClass(memberClass, null);
        return manager.areElementsEquivalent(placeTopLevelClass, memberTopLevelClass);
      }
      else {
        return fileResolveScope instanceof PsiClass &&
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
}