// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


/**
 * A {@link CodeInsightTestFixture} extended a bit for Java-dependent tests.
 * <p>
 * Can be created using {@link JavaTestFixtureFactory#createCodeInsightFixture(IdeaProjectTestFixture)}.
 *
 * @see LightJavaCodeInsightFixtureTestCase
 * @see LightJavaCodeInsightFixtureTestCase4
 * @see LightJavaCodeInsightFixtureTestCase5
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

  /**
   * @param file file to allow tree access for. 
   *             By default, it's prohibited to read full content of *.java and *.class files, except the one opened in the editor.
   *             Do not overuse this: it's generally expected that only stubs of non-opened files should be accessible. 
   */
  void allowTreeAccessForFile(@NotNull VirtualFile file);
}
