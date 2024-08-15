// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Matcher;

public class DefaultClassNavigationContributor implements ChooseByNameContributorEx, GotoClassContributor, PossiblyDumbAware {
  @Override
  public String getQualifiedName(final @NotNull NavigationItem item) {
    if (item instanceof PsiClass) {
      return getQualifiedNameForClass((PsiClass)item);
    }
    return null;
  }

  public static String getQualifiedNameForClass(PsiClass psiClass) {
    final String qName = psiClass.getQualifiedName();
    if (qName != null) return qName;

    final String containerText = SymbolPresentationUtil.getSymbolContainerText(psiClass);
    return containerText + "." + psiClass.getName();
  }

  @Override
  public String getQualifiedNameSeparator() {
    return "$";
  }

  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    Project project = scope.getProject();
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      getPsiShortNamesCache(project).processAllClassNames(processor, scope, filter);
    });
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      final @NotNull Processor<? super NavigationItem> processor,
                                      final @NotNull FindSymbolParameters parameters) {
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      DefaultClassProcessor defaultClassProcessor = new DefaultClassProcessor(processor, parameters, false);
      getPsiShortNamesCache(parameters.getProject())
        .processClassesWithName(name, defaultClassProcessor, parameters.getSearchScope(), parameters.getIdFilter());
    });
  }

  private static PsiShortNamesCache getPsiShortNamesCache(@NotNull Project project) {
    Set<Language> withoutLanguages = IgnoreLanguageInDefaultProvider.getIgnoredLanguages();
    return PsiShortNamesCache.getInstance(project).withoutLanguages(withoutLanguages);
  }

  public static class DefaultClassProcessor implements Processor<PsiClass> {
    private final @NotNull Processor<? super NavigationItem> processor;
    private final @Nullable MinusculeMatcher innerClassMatcher;
    private final boolean allowNonPhysicalClasses;
    private final boolean isAnnotation;

    DefaultClassProcessor(final @NotNull Processor<? super NavigationItem> processor, final @NotNull FindSymbolParameters parameters,
                          boolean allowNonPhysicalClasses) {
      this.processor = processor;
      this.innerClassMatcher = getInnerClassMatcher(parameters);
      this.allowNonPhysicalClasses = allowNonPhysicalClasses;
      isAnnotation = parameters.getLocalPatternName().startsWith("@");
    }

    @Override
    public boolean process(PsiClass aClass) {
      if (!DefaultSymbolNavigationContributor.isOpenable(aClass) || (!allowNonPhysicalClasses && !aClass.isPhysical())) {
        return true;
      }
      if (isAnnotation && !aClass.isAnnotationType()) return true;
      if (innerClassMatcher != null) {
        if (aClass.getContainingClass() == null) return true;
        String jvmQName = ClassUtil.getJVMClassName(aClass);
        if (jvmQName == null || !innerClassMatcher.matches(StringUtil.getShortName(jvmQName))) return true;
      }
      return processor.process(aClass);
    }

    private static @Nullable MinusculeMatcher getInnerClassMatcher(@NotNull FindSymbolParameters parameters) {
      String namePattern = StringUtil.getShortName(parameters.getCompletePattern());
      boolean hasDollar = namePattern.contains("$");
      if (hasDollar) {
        Matcher matcher = ChooseByNamePopup.patternToDetectAnonymousClasses.matcher(namePattern);
        if (matcher.matches()) {
          namePattern = matcher.group(1);
          hasDollar = namePattern.contains("$");
        }
      }
      return hasDollar ? NameUtil.buildMatcher("*" + namePattern).build() : null;
    }
  }

  @Override
  public @Nullable Language getElementLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}