// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

/**
 * User: anna
 */
public class InplaceRenameInvariantTest extends LightJavaCodeInsightTestCase {
  public void testStartCaretPosition() {
    String text = """
       class <caret>Test {
       }""";
    doTestPositionInvariance(text, false, false);
  }

  public void testMiddleCaretPosition() {
    String text = """
       class Te<caret>st {
       }""";
    doTestPositionInvariance(text, false, false);
  }

  public void testEndCaretPosition() {
    String text = """
       class Test<caret> {
       }""";
    doTestPositionInvariance(text, false, false);
  }

  public void testEndCaretPositionTyping() {
    String text = """
       class Test {
         Test<caret> myTest;
       }""";
    doTestPositionInvariance(text, false, false);
  }

  public void testStartCaretPositionPreselect() {
    String text = """
       class <caret>Test {
       }""";
    doTestPositionInvariance(text, true, false);
  }

  public void testMiddleCaretPositionPreselect() {
    String text = """
       class Te<caret>st {
       }""";
    doTestPositionInvariance(text, true, false);
  }

  public void testEndCaretPositionPreselect() {
    String text = """
       class Test<caret> {
       }""";
    doTestPositionInvariance(text, true, false);
  }

  private void doTestPositionInvariance(String text, final boolean preselect, final boolean checkTyping) {
    configure(text);
    boolean oldPreselectSetting = getEditor().getSettings().isPreselectRename();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setPreselectRename(preselect);
      int offset = getEditor().getCaretModel().getOffset();
      final PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.getInstance().getAllAccepted());

      assertNotNull(element);

      MemberInplaceRenameHandler handler = new MemberInplaceRenameHandler();

      handler.doRename(element, getEditor(), null);

      if (checkTyping) {
        type("1");
        offset++;
      }
      assertEquals(offset, getEditor().getCaretModel().getOffset());
    }
    finally {
      getEditor().getSettings().setPreselectRename(oldPreselectSetting);
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assertNotNull(state);
      state.gotoEnd(false);
    }
  }

  private void configure(String text) {
    configureFromFileText("a.java", text);
  }
}
