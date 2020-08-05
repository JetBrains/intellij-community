// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class JavaTelescope {
  private static final int TOO_MANY_USAGES = -1;

  public static String usagesHint(@NotNull PsiMember member, @NotNull PsiFile file) {
    Project project = file.getProject();

    AtomicInteger totalUsageCount = new AtomicInteger();
    ProgressIndicator progress = ObjectUtils.notNull(ProgressIndicatorProvider.getGlobalProgressIndicator(), /*todo remove*/new EmptyProgressIndicator());
    List<PsiMember> things =
      member instanceof PsiMethod ? new ArrayList<>(DeepestSuperMethodsSearch.search((PsiMethod)member).findAll()) : Collections.singletonList(member);
    if (things.isEmpty()) {
      things.add(member);
    }
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(things, progress, e -> {
      int count = usagesCount(project, file, e, progress);
      int newCount = totalUsageCount.updateAndGet(old -> count == TOO_MANY_USAGES ? TOO_MANY_USAGES : old + count);
      return newCount != TOO_MANY_USAGES;
    });
    if (totalUsageCount.get() == TOO_MANY_USAGES || totalUsageCount.get() == 0) return null;
    String format = "{0,choice, 0#no usages|1#1 usage|2#{0,number} usages}";
    return MessageFormat.format(format, totalUsageCount.get());
  }

  private static int usagesCount(@NotNull Project project,
                                 @NotNull PsiFile containingFile,
                                 @NotNull final PsiMember member,
                                 @NotNull ProgressIndicator progress) {
    AtomicInteger count = new AtomicInteger();
    boolean ok = UnusedSymbolUtil.processUsages(project, containingFile, member, progress, null, info -> {
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

  public static int collectInheritingClasses(@NotNull PsiClass aClass) {
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

  public static int collectOverridingMethods(@NotNull final PsiMethod method) {

    AtomicInteger count = new AtomicInteger();
    OverridingMethodsSearch.search(method).forEach((Consumer<? super PsiMethod>)__ -> count.incrementAndGet());

    return count.get();
  }
}
