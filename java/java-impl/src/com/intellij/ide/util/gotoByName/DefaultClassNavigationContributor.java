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
package com.intellij.ide.util.gotoByName;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class DefaultClassNavigationContributor implements ChooseByNameContributorEx, GotoClassContributor {
  @Override
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    if (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping) {
      GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
      List<String> result = new ArrayList<>();
      Processor<String> processor = Processors.cancelableCollectProcessor(result);

      processNames(processor, scope, IdFilter.getProjectIdFilter(project, includeNonProjectItems));

      return ArrayUtil.toStringArray(result);
    }

    return PsiShortNamesCache.getInstance(project).getAllClassNames();
  }

  @Override
  @NotNull
  public NavigationItem[] getItemsByName(String name, final String pattern, Project project, boolean includeNonProjectItems) {
    List<NavigationItem> result = new ArrayList<>();
    Processor<NavigationItem> processor = Processors.cancelableCollectProcessor(result);
    processElementsWithName(name, processor, FindSymbolParameters.wrap(pattern, project, includeNonProjectItems));

    return result.isEmpty() ? NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY :
           result.toArray(NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY);
  }

  @Override
  public String getQualifiedName(final NavigationItem item) {
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
  public void processNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    PsiShortNamesCache.getInstance(scope.getProject()).processAllClassNames(processor, scope, filter);
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull final Processor<NavigationItem> processor,
                                      @NotNull final FindSymbolParameters parameters) {
    String namePattern = StringUtil.getShortName(parameters.getCompletePattern());
    boolean hasDollar = namePattern.contains("$");
    if (hasDollar) {
      Matcher matcher = ChooseByNamePopup.patternToDetectAnonymousClasses.matcher(namePattern);
      if (matcher.matches()) {
        namePattern = matcher.group(1);
        hasDollar = namePattern.contains("$");
      }
    }
    final MinusculeMatcher innerMatcher = hasDollar ? NameUtil.buildMatcher("*" + namePattern).build() : null;
    PsiShortNamesCache.getInstance(parameters.getProject()).processClassesWithName(name, new Processor<PsiClass>() {
      final boolean isAnnotation = parameters.getLocalPatternName().startsWith("@");

      @Override
      public boolean process(PsiClass aClass) {
        if (!isPhysical(aClass)) return true;
        if (isAnnotation && !aClass.isAnnotationType()) return true;
        if (innerMatcher != null) {
          if (aClass.getContainingClass() == null) return true;
          String jvmQName = ClassUtil.getJVMClassName(aClass);
          if (jvmQName == null || !innerMatcher.matches(StringUtil.getShortName(jvmQName))) return true;
        }
        return processor.process(aClass);
      }
    }, parameters.getSearchScope(), parameters.getIdFilter());
  }

  @Nullable
  @Override
  public Language getElementLanguage() {
    return JavaLanguage.INSTANCE;
  }

  private static boolean isPhysical(PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    return file != null && file.getVirtualFile() != null && aClass.isPhysical();
  }
}