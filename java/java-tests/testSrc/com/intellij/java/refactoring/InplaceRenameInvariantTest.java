// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * User: anna
 */
@TestApplication
public class InplaceRenameInvariantTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testStartCaretPosition() {
    String text = """
       class <caret>Test {
       }""";
    doTestPositionInvariance(text, false, false);
  }

  @Test
  public void testMiddleCaretPosition() {
    String text = """
       class Te<caret>st {
       }""";
    doTestPositionInvariance(text, false, false);
  }

  @Test
  public void testEndCaretPosition() {
    String text = """
       class Test<caret> {
       }""";
    doTestPositionInvariance(text, false, false);
  }

  @Test
  public void testEndCaretPositionTyping() {
    String text = """
       class Test {
         Test<caret> myTest;
       }""";
    doTestPositionInvariance(text, false, false);
  }

  @Test
  public void testStartCaretPositionPreselect() {
    String text = """
       class <caret>Test {
       }""";
    doTestPositionInvariance(text, true, false);
  }

  @Test
  public void testMiddleCaretPositionPreselect() {
    String text = """
       class Te<caret>st {
       }""";
    doTestPositionInvariance(text, true, false);
  }

  @Test
  public void testEndCaretPositionPreselect() {
    String text = """
       class Test<caret> {
       }""";
    doTestPositionInvariance(text, true, false);
  }

  private void doTestPositionInvariance(String text, final boolean preselect, final boolean checkTyping) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure(text);
    myFixture.runInCommand(() -> {
      boolean oldPreselectSetting = myFixture.getEditor().getSettings().isPreselectRename();
      try {
        TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
        myFixture.getEditor().getSettings().setPreselectRename(preselect);
        int offset = myFixture.getEditor().getCaretModel().getOffset();
        final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());

        assertNotNull(element);

        MemberInplaceRenameHandler handler = new MemberInplaceRenameHandler();

        handler.doRename(element, myFixture.getEditor(), null);

        if (checkTyping) {
          myFixture.type("1");
          offset++;
        }
        assertEquals(offset, myFixture.getEditor().getCaretModel().getOffset());
      }
      finally {
        myFixture.getEditor().getSettings().setPreselectRename(oldPreselectSetting);
        TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        assertNotNull(state);
        state.gotoEnd(false);
      }
    });
  }

  private void configure(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", text);
  }
}
