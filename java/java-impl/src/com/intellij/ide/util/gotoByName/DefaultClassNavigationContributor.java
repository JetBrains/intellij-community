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
import com.intellij.openapi.project.PossiblyDumbAware;
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
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

public class DefaultClassNavigationContributor implements ChooseByNameContributorEx, GotoClassContributor, PossiblyDumbAware {
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
    Project project = scope.getProject();
    FileBasedIndex.getInstance().ignoreDumbMode(() -> {
      PsiShortNamesCache.getInstance(project).processAllClassNames(processor, scope, filter);
    }, project);
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
    FileBasedIndex.getInstance().ignoreDumbMode(() -> {
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
    }, parameters.getProject());
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

  @Override
  public boolean isDumbAware() {
    return FileBasedIndex.indexAccessDuringDumbModeEnabled();
  }
}