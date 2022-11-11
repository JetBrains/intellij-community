// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit.references;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.concurrent.ConcurrentMap;

final class JUnitReferenceContributor extends PsiReferenceContributor {
  private static PsiElementPattern.Capture<PsiLanguageInjectionHost> getElementPattern(String annotation, String paramName) {
    return PlatformPatterns.psiElement(PsiLanguageInjectionHost.class).and(new FilterPattern(new TestAnnotationFilter(annotation, paramName)));
  }

  private static PsiElementPattern.Capture<PsiLanguageInjectionHost> getEnumSourceNamesPattern() {
    return getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ENUM_SOURCE, "names")
      .withAncestor(4, PlatformPatterns.psiElement(PsiAnnotation.class).and(new PsiJavaElementPattern<>(
        new InitialPatternCondition<>(PsiAnnotation.class) {
          @Override
          public boolean accepts(@Nullable Object o, ProcessingContext context) {
            if (o instanceof PsiAnnotation) {
              PsiAnnotationMemberValue mode = ((PsiAnnotation)o).findAttributeValue("mode");
              if (mode instanceof PsiReferenceExpression) {
                String referenceName = ((PsiReferenceExpression)mode).getReferenceName();
                return "INCLUDE".equals(referenceName) || "EXCLUDE".equals(referenceName);
              }
            }
            return false;
          }
        })));
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE, "value"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new MethodSourceReference[]{new MethodSourceReference((PsiLanguageInjectionHost)element)};
      }
    });
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_CONDITION_PROVIDER_ENABLED_IF, "value"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new DisabledIfEnabledIfReference[]{new DisabledIfEnabledIfReference((PsiLanguageInjectionHost)element)};
      }
    });
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_CONDITION_PROVIDER_DISABLED_IF, "value"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new DisabledIfEnabledIfReference[]{new DisabledIfEnabledIfReference((PsiLanguageInjectionHost)element)};
      }
    });
    registrar.registerReferenceProvider(getEnumSourceNamesPattern(), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new EnumSourceReference[] {new EnumSourceReference((PsiLanguageInjectionHost)element)};
      }
    });
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE,
                                                          "resources"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return FileReferenceSet.createSet(element, false, false, false).getAllReferences();
      }
    });
  }

  private static class TestAnnotationFilter implements ElementFilter {

    private final String myAnnotation;
    private final String myParameterName;

    TestAnnotationFilter(String annotation, @NotNull @NonNls String parameterName) {
      myAnnotation = annotation;
      myParameterName = parameterName;
    }

    @Override
    public boolean isAcceptable(Object __, PsiElement context) {
      if (context == null) {
        return false;
      }
      if (DumbService.isDumb(context.getProject())) {
        return false;
      }
      if (getMapOfAnnotationClasses(context.getContainingFile()).get(myAnnotation) == null) {
        return false;
      }
      UElement type = UastContextKt.toUElement(context, UElement.class);
      if (type == null) return false;
      UElement element = type.getUastParent();
      boolean parameterFound = false;
      for (int i = 0; i < 5 && element != null; i++) {
        if (element instanceof UFile || 
            element instanceof UDeclaration || 
            element instanceof UDeclarationsExpression || 
            element instanceof UJumpExpression || 
            element instanceof UBlockExpression) {
          return false;
        }
        if (element instanceof UNamedExpression) {
          UNamedExpression uPair = (UNamedExpression)element;
          String name = ObjectUtils.notNull(uPair.getName(), PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
          if (!myParameterName.equals(name)) {
            return false;
          }
          parameterFound = true;
        }
        if (element instanceof UAnnotation) {
          UAnnotation annotation = (UAnnotation)element;
          return parameterFound && myAnnotation.equals(annotation.getQualifiedName());
        }
        element = element.getUastParent();
      }
      return false;
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return PsiLanguageInjectionHost.class.isAssignableFrom(hintClass);
    }
  }

  private static ConcurrentMap<String, PsiClass> getMapOfAnnotationClasses(PsiFile containingFile) {
    return CachedValuesManager.getCachedValue(containingFile, () -> {
      Project project = containingFile.getProject();
      return new CachedValueProvider.Result<>(
        ConcurrentFactoryMap.createMap(annoName -> JavaPsiFacade.getInstance(project).findClass(annoName, containingFile.getResolveScope())),
        ProjectRootModificationTracker.getInstance(project));
    });
  }
}
