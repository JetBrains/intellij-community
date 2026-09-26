// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.java.JavaImportOptimizer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class OptimizeImportsTestCase extends LightJavaCodeInsightFixtureTestCase {
  
  protected abstract @NotNull String getExtension();

  protected void doTest() {
    doTest(null);
  }

  protected void doTest(@Nullable String notification) {
    String extension = getExtension();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String fileName = getTestName(false) + extension;
      try {
        PsiFile file = myFixture.configureByFile(fileName);

        Runnable runnable = new JavaImportOptimizer().processFile(file);
        runnable.run();
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        myFixture.checkResultByFile(getTestName(false) + "_after" + extension);
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
