// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class UnnecessaryPsvmModifierInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/style/unnecessary_psvm_modifier";
  }



  private void doTest() {
    myFixture.enableInspections(new UnnecessaryModifierInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testOldJavaWithModifiers() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> doTest());
  }

  public void testImplicitClassWithModifiers() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> doTest());
  }

  public void testNormalClassWithModifiers() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> doTest());
  }

  public void testNormalClassWithModifiersClassIsUsed() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      myFixture.addClass("""
                           import test.NormalClassWithModifiersClassIsUsed;
                           class Foo {
                             public static void main(String[] args){
                               NormalClassWithModifiersClassIsUsed.main(args);
                             }
                           }
                           """);
      doTest();
    });
  }
}
