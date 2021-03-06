// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageMatcher;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

class FileLanguageGlobalScope extends DelegatingGlobalSearchScope {

  private final Project myProject;
  private final @NotNull Collection<? extends LanguageMatcher> myLanguageMatchers;

  FileLanguageGlobalScope(@NotNull Project project,
                          @NotNull GlobalSearchScope baseScope,
                          @NotNull Collection<? extends LanguageMatcher> languageMatchers) {
    super(baseScope);
    myProject = project;
    myLanguageMatchers = languageMatchers;
  }

  FileLanguageGlobalScope(Project project, GlobalSearchScope scope, LanguageMatcher matcher) {
    this(project, scope, Collections.singleton(matcher));
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return super.contains(file) && matchesLanguage(file);
  }

  private boolean matchesLanguage(@NotNull VirtualFile file) {
    Language baseLanguage = LanguageUtil.getLanguageForPsi(myProject, file);
    if (baseLanguage instanceof TemplateLanguage) {
      FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(file);
      if (viewProvider == null) {
        return false;
      }
      return ContainerUtil.or(
        viewProvider.getLanguages(),
        fileLanguage -> ContainerUtil.or(myLanguageMatchers, matcher -> matcher.matchesLanguage(fileLanguage))
      );
    }
    else if (baseLanguage != null) {
      return ContainerUtil.or(myLanguageMatchers, matcher -> matcher.matchesLanguage(baseLanguage));
    }
    else {
      return false;
    }
  }

  @NotNull
  @Override
  public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    if (scope instanceof FileLanguageGlobalScope) {
      FileLanguageGlobalScope other = (FileLanguageGlobalScope)scope;
      if (myBaseScope == other.myBaseScope) {
        Collection<LanguageMatcher> intersection = ContainerUtil.intersection(myLanguageMatchers, other.myLanguageMatchers);
        if (intersection.isEmpty()) {
          return GlobalSearchScope.EMPTY_SCOPE;
        }
        else {
          return new FileLanguageGlobalScope(myProject, myBaseScope, intersection);
        }
      }
    }
    return super.intersectWith(scope);
  }

  @NotNull
  @Override
  public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope instanceof FileLanguageGlobalScope) {
      FileLanguageGlobalScope other = (FileLanguageGlobalScope)scope;
      if (myBaseScope == other.myBaseScope) {
        Collection<LanguageMatcher> intersection = ContainerUtil.union(myLanguageMatchers, other.myLanguageMatchers);
        return new FileLanguageGlobalScope(myProject, myBaseScope, intersection);
      }
    }
    return super.uniteWith(scope);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    FileLanguageGlobalScope scope = (FileLanguageGlobalScope)o;

    if (!myProject.equals(scope.myProject)) return false;
    if (!myLanguageMatchers.equals(scope.myLanguageMatchers)) return false;

    return true;
  }

  @Override
  public int calcHashCode() {
    int result = super.calcHashCode();
    result = 31 * result + myProject.hashCode();
    result = 31 * result + myLanguageMatchers.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "(restricted by file languages: " +
           myLanguageMatchers +
           " in " +
           myBaseScope +
           ")";
  }
}
