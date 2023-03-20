// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.concurrency.JobLauncher;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Utility class to support Code Vision for java
 */
final class JavaTelescope {
  private static final int TOO_MANY_USAGES = -1;

  static String usagesHint(@NotNull PsiMember member, @NotNull PsiFile file) {
    int totalUsageCount = UsagesCountManager.getInstance(member.getProject()).countMemberUsages(file, member);
    if (totalUsageCount == TOO_MANY_USAGES) return null;
    if (!Registry.is("code.lens.java.show.0.usages") && totalUsageCount == 0) return null;
    return JavaBundle.message("usages.telescope", totalUsageCount);
  }

  public static int usagesCount(@NotNull PsiFile file, List<PsiMember> members, SearchScope scope) {
    Project project = file.getProject();
    ProgressIndicator progress = ObjectUtils.notNull(ProgressIndicatorProvider.getGlobalProgressIndicator(), /*todo remove*/new EmptyProgressIndicator());
    AtomicInteger totalUsageCount = new AtomicInteger();
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(members, progress, member -> {
      int count = usagesCount(project, file, member, scope, progress);
      int newCount = totalUsageCount.updateAndGet(old -> count == TOO_MANY_USAGES ? TOO_MANY_USAGES : old + count);
      return newCount != TOO_MANY_USAGES;
    });
    return totalUsageCount.get();
  }

  private static int usagesCount(@NotNull Project project,
                                 @NotNull PsiFile containingFile,
                                 @NotNull final PsiMember member,
                                 @NotNull SearchScope scope,
                                 @NotNull ProgressIndicator progress) {
    SearchScope useScope = UnusedSymbolUtil.getUseScope(member);
    AtomicInteger count = new AtomicInteger();
    boolean ok = UnusedSymbolUtil.processUsages(project, containingFile, useScope.intersectWith(scope), member, progress, null, info -> {
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

  static int collectInheritingClasses(@NotNull PsiClass aClass) {
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return 0;
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      return 0; // It's useless to have overridden markers for object.
    }

    AtomicInteger count = new AtomicInteger();
    ClassInheritorsSearch.INSTANCE.createQuery(new ClassInheritorsSearch.SearchParameters(aClass, aClass.getUseScope(), true, true, true))
      .forEach((Consumer<? super PsiClass>)__ -> count.incrementAndGet());

    return count.get();
  }

  static int collectOverridingMethods(@NotNull PsiMethod method) {
    AtomicInteger count = new AtomicInteger();
    OverridingMethodsSearch.search(method).forEach((Consumer<? super PsiMethod>)__ -> count.incrementAndGet());

    return count.get();
  }
}
