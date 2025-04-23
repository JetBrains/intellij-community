// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


/**
 * A {@link CodeInsightTestFixture} extended a bit for Java-dependent tests.
 */
public interface JavaCodeInsightTestFixture extends CodeInsightTestFixture {
  JavaPsiFacadeEx getJavaFacade();

  PsiClass addClass(@Language("JAVA") final @NotNull @NonNls String classText);

  /**
   * Finds class by given fully qualified name in {@link GlobalSearchScope#allScope(Project)}.
   *
   * @param name Qualified name of class to find.
   * @return Class instance.
   */
  @NotNull PsiClass findClass(@NotNull @NonNls String name);

  @NotNull PsiPackage findPackage(@NotNull @NonNls String name);
}
