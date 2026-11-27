// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.migration;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.migration.ForCanBeForeachInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ForCanBeForeachFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ForCanBeForeachInspection()), "migration/for_can_be_foreach",
                        InspectionGadgetsBundle.message("foreach.replace.quickfix"));

  @Test
  public void testParenthesis() { ig.get().doTest(); }
  @Test
  public void testParenthesisArrayLoop() { ig.get().doTest(); }
  @Test
  public void testParenthesisArrayLoop2() { ig.get().doTest(); }
  @Test
  public void testParenthesisIteratorLoop() { ig.get().doTest(); }
  @Test
  public void testInstanceofAndWhitespace() { ig.get().doTest(); }
  @Test
  public void testQualifyWithThis1() { ig.get().doTest(); }
  @Test
  public void testQualifyWithThis2() { ig.get().doTest(); }
  @Test
  public void testQualifyWithThisInner() { ig.get().doTest(); }
  @Test
  public void testNoQualifier() { ig.get().doTest(); }
  @Test
  public void testForThisClass() { ig.get().doTest(); }
  @Test
  public void testForOuterClass() { ig.get().doTest(); }
  @Test
  public void testForOuterClassIterator() { ig.get().doTest(); }
  @Test
  public void testForQualifiedArray() { ig.get().doTest(); }
  @Test
  public void testForFieldName() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    JavaCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    try {
      ig.get().doTest();
    }
    finally {
      settings.FIELD_NAME_PREFIX = oldPrefix;
    }
  }
  @Test
  public void testRawCollection() { ig.get().doTest(); }
  @Test
  public void testArrayUnboxing() { ig.get().doTest(); }
  @Test
  public void testListUnboxing() { ig.get().doTest(); }
  @Test
  public void testIteratorUnboxing() { ig.get().doTest(); }

  /// Regression test for IDEA-386737.
  @Test
  public void testUpdateInspectionOptionFixHasNoFixAll() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    ForCanBeForeachInspection inspection = new ForCanBeForeachInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByText("Test.java", """
      import java.util.List;

      class Test {
        void foo(List<String> list) {
          <caret>for (int i = 0; i < list.size(); i++) {
            System.out.println(list.get(i));
          }
        }
      }
      """);

    IntentionAction optionFix = myFixture.findSingleIntention(
      InspectionGadgetsBundle.message("for.can.be.foreach.fix.no.indexed"));
    IntentionAction fixAll = IntentionManager.getInstance().createFixAllIntention(new LocalInspectionToolWrapper(inspection), optionFix);
    assertNull(fixAll);
  }

}
