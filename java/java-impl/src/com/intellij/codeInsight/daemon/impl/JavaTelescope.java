// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Utility class to support Code Vision for java
 */
final class JavaTelescope {
  static final int TOO_MANY_USAGES = -1;

  static class UsagesHint {
    public @Nls String hint;
    public int count;
    UsagesHint(@Nls String hint, int count) {
        this.hint = hint;
        this.count = count;
    }
  }

  static @Nullable UsagesHint usagesHint(@NotNull PsiMember member, @NotNull PsiFile psiFile) {
    int totalUsageCount = UsagesCountManager.getInstance(member.getProject()).countMemberUsages(psiFile, member);
    if (totalUsageCount == TOO_MANY_USAGES) return null;
    if (totalUsageCount < AdvancedSettings.getInt("code.vision.java.minimal.usages")) return null;
    return new UsagesHint(JavaBundle.message("usages.telescope", totalUsageCount), totalUsageCount);
  }

  public static int usagesCount(@NotNull PsiFile psiFile, List<PsiMember> members, SearchScope scope) {
    Project project = psiFile.getProject();
    ProgressIndicator progress = ObjectUtils.notNull(ProgressIndicatorProvider.getGlobalProgressIndicator(), /*todo remove*/new EmptyProgressIndicator());
    AtomicInteger totalUsageCount = new AtomicInteger();

    if (Registry.is("java.telescope.usages.single.threaded", true)) {
      for (PsiMember member : members) {
        if (!countUsagesForMember(psiFile, scope, member, project, totalUsageCount)) break;
      }
    } else {
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(members, progress, member -> {
        return countUsagesForMember(psiFile, scope, member, project, totalUsageCount);
      });
    }

    return totalUsageCount.get();
  }

  /**
   * Counts usages for the provided {@code member} and returns {@code true} if consecutive members should be processed.
   */
  private static boolean countUsagesForMember(@NotNull PsiFile psiFile,
                                              SearchScope scope,
                                              PsiMember member,
                                              Project project,
                                              AtomicInteger totalUsageCount) {
    int count = usagesCount(project, psiFile, member, scope);
    int newCount = totalUsageCount.updateAndGet(old -> count == TOO_MANY_USAGES ? TOO_MANY_USAGES : old + count);
    if (newCount == TOO_MANY_USAGES) return false;
    return true;
  }


  private static int usagesCount(@NotNull Project project,
                                 @NotNull PsiFile containingFile,
                                 final @NotNull PsiMember member,
                                 @NotNull SearchScope scope) {
    SearchScope searchScope = getSearchScope(project, member, scope);
    AtomicInteger count = new AtomicInteger();
    boolean ok = UnusedSymbolUtil.processUsages(project, containingFile, searchScope, member, null, info -> {
      PsiFile psiFile = info.getFile();
      if (psiFile == null) {
        return true;
      }
      int offset = info.getNavigationOffset();
      if (offset == -1) return true;
      count.incrementAndGet();
      return true;
    });
    if (!ok) {
      return TOO_MANY_USAGES;
    }
    return count.get();
  }

  private static final FileType[] ourFileTypesToIgnore =  new FileType[] { HtmlFileType.INSTANCE };

  private static @NotNull SearchScope getSearchScope(@NotNull Project project, @NotNull PsiMember member, @NotNull SearchScope scope) {
    SearchScope useScope = UnusedSymbolUtil.getUseScope(member);
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    GlobalSearchScope restrictedScope = GlobalSearchScope.getScopeRestrictedByFileTypes(projectScope, ourFileTypesToIgnore);
    return useScope.intersectWith(GlobalSearchScope.notScope(restrictedScope)).intersectWith(scope);
  }

  static int collectInheritingClasses(@NotNull PsiClass aClass) {
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return 0;
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      return 0; // It's useless to have overridden markers for object.
    }
    
    if (DeclarationSearchUtils.isTooExpensiveToSearch(aClass, false)) {
      return 0;
    }

    AtomicInteger count = new AtomicInteger();
    ClassInheritorsSearch.INSTANCE.createQuery(new ClassInheritorsSearch.SearchParameters(aClass, aClass.getUseScope(), true, true, true))
      .asIterable()
      .forEach((Consumer<? super PsiClass>)__ -> count.incrementAndGet());

    return count.get();
  }

  static int collectOverridingMethods(@NotNull PsiMethod method) {
    AtomicInteger count = new AtomicInteger();
    OverridingMethodsSearch.search(method).asIterable().forEach((Consumer<? super PsiMethod>)__ -> count.incrementAndGet());

    return count.get();
  }
}
