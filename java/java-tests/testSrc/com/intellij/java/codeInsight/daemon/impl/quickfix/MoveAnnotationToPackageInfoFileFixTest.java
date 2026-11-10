// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class MoveAnnotationToPackageInfoFileFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/moveAnnotationToPackage");
  }

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("com/example/Simple.java");
    IntentionAction action = myFixture.findSingleIntention(JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name"));
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
         package com.example;
         ----------
         @Deprecated
         package com.example;""", text);
  }

  @Test
  public void testImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.example.anno; @interface Anno {}");
    myFixture.configureByFile("com/example/Imports.java");
    IntentionAction action = myFixture.findSingleIntention(JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name"));
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
         package com.example;
                             
         import com.example.anno.Anno;
         
         class Imports {}
         ----------
         @Deprecated @Anno
         package com.example;
         
         import com.example.anno.Anno;""", text);
  }
}
