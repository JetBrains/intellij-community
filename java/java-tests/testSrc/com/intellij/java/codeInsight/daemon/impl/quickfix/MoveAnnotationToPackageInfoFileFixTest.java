// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class MoveAnnotationToPackageInfoFileFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() {
    myFixture.configureByFile("com/example/Simple.java");
    IntentionAction action = myFixture.findSingleIntention(JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name"));
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
         package com.example;
         ----------
         @Deprecated
         package com.example;""", text);
  }

  public void testImports() {
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

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/moveAnnotationToPackage";
  }
}
