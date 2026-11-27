// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightSubFixture;

/**
 * The heavy Java fixture of the generics tests, with a new project for each test.
 * The project language level is 1.5, and the sample classes of {@code psi/types/src} are in a source root of the main module.
 */
public final class GenericsFixture {
  private GenericsFixture() {
  }

  /**
   * Creates the fixture. Declare it as an instance field.
   */
  public static @NotNull TestFixture<HeavyJavaCodeInsightFixture> genericsFixture() {
    return heavyJavaCodeInsightSubFixture("genericsFixture", context -> new HeavyJavaCodeInsightFixture(context, JavaTestUtil.getJavaTestDataPath()) {
      @Override
      protected @NotNull LanguageLevel getProjectLanguageLevel() {
        return LanguageLevel.JDK_1_5;
      }

      @Override
      protected void setUp() {
        super.setUp();
        final String commonPath = JavaTestUtil.getJavaTestDataPath().replace(File.separatorChar, '/') + "/psi/types/src";
        VirtualFile commonRoot = WriteAction.computeAndWait(() -> StandardFileSystems.local().refreshAndFindFileByPath(commonPath));
        PsiTestUtil.addSourceRoot(getModule(), commonRoot);
      }
    });
  }
}
