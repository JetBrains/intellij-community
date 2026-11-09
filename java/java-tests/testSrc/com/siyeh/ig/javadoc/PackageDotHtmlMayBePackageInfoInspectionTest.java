// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PackageDotHtmlMayBePackageInfoInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new PackageDotHtmlMayBePackageInfoInspection());

  @Test
  public void testSimple() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByFile("com/example/package.html");
    myFixture.testHighlighting(true, false, false);
    IntentionAction action = myFixture.findSingleIntention("Convert to 'package-info.java'");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiFile psiFile = myFixture.getFile();
      PsiDirectory directory = psiFile.getContainingDirectory();
      assertTrue(psiFile.isValid());
      myFixture.launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      assertFalse(psiFile.isValid());
      PsiFile packageInfo = directory.findFile("package-info.java");
      assertNotNull(packageInfo);
      assertEquals("""
                     /**
                      * Hello from documentation!
                      * <p>
                      * This is a package.
                      * </p>
                      */
                     package com.example;""", packageInfo.getText());
    });
  }
}