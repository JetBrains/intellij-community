// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WhileLoopSpinsOnFieldInspectionTest extends LightJavaInspectionTestCase {

  public void testWhileLoopSpinsOnField() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, this::doTest);
  }

  public void testConditionBoundedBuffer() {
    // Sample from JCIP
    // See IDEA-364908
    doTest();
  }

  public void testMultiFileFix() {
    myFixture.addFileToProject("F1.java", """
      public class F1 {
          boolean f;
      }
      """);
    doTest();
    IntentionAction action = myFixture.findSingleIntention("Make 'f' volatile and add Thread.onSpinWait()");
    var text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
      class F2 extends F1 {
          void test() {
              while (f) {
                  Thread.onSpinWait();
                         
              }
          }
      }
                         
      ----------
      public class F1 {
          volatile boolean f;
      }
      """, text);
  }
  
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    WhileLoopSpinsOnFieldInspection inspection = new WhileLoopSpinsOnFieldInspection();
    inspection.ignoreNonEmtpyLoops = false;
    return inspection;
  }
}