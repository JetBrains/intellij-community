// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.java.JavaImportOptimizer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The check of an optimize imports test for a JUnit 5 test class.
 */
@TestOnly
public final class OptimizeImportsTestUtil {
  private static final Logger LOG = Logger.getInstance(OptimizeImportsTestUtil.class);

  private OptimizeImportsTestUtil() {
  }

  /**
   * Configures {@code <testName><extension>} of the test data, optimizes its imports with {@link JavaImportOptimizer},
   * and compares the file with {@code <testName>_after<extension>}.
   * When {@code notification} is not null, the user notification of the optimizer must be equal to it.
   * The fixture configures the file outside of the write command, because the new file starts a dumb mode.
   * The other steps run in one write command on the EDT.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull String testName,
                            @NotNull String extension,
                            @Nullable String notification) {
    PsiFile file = fixture.configureByFile(testName + extension);
    WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
      try {
        Runnable runnable = new JavaImportOptimizer().processFile(file);
        runnable.run();
        PostprocessReformattingAspect.getInstance(fixture.getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
        fixture.checkResultByFile(testName + "_after" + extension);
        PsiTestUtil.checkFileStructure(file);
        if (notification != null && runnable instanceof ImportOptimizer.CollectingInfoRunnable infoRunnable) {
          assertEquals(notification, infoRunnable.getUserNotificationInfo());
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }
}
