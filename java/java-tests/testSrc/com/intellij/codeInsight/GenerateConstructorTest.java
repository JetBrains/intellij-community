/*
 * @author ven
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class GenerateConstructorTest extends LightCodeInsightTestCase {
  public void testAbstractClass() throws Exception { doTest(); }
  public void testPackageLocalClass() throws Exception { doTest(); }
  public void testPrivateClass() throws Exception { doTest(); }
  public void testBoundComments() throws Exception { doTest(); }
  public void testSameNamedFields() throws Exception { doTest(); }

  public void testImmediatelyAfterRBrace() throws Exception {    // IDEADEV-28811
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings();
    final int old = settings.CLASS_BRACE_STYLE;
    settings.CLASS_BRACE_STYLE = CodeStyleSettings.NEXT_LINE;
    try {
      doTest();
    }
    finally {
      settings.CLASS_BRACE_STYLE = old;
    }
  }

  public void testBoundCommentsKeepsBlankLine() throws Exception {
    CodeStyleSettingsManager styleSettingsManager = CodeStyleSettingsManager.getInstance(getProject());
    final CodeStyleSettings settings = styleSettingsManager.getCurrentSettings();
    settings.BLANK_LINES_AFTER_CLASS_HEADER = 1;
    doTest();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/generateConstructor/before" +
                    name +
                    ".java");
    new GenerateConstructorHandler(){
      @Override
      protected ClassMember[] chooseMembers(ClassMember[] members,
                                            boolean allowEmptySelection,
                                            boolean copyJavadocCheckbox,
                                            Project project) {
        return members;
      }
    }.invoke(getProject(), getEditor(), getFile());
    checkResultByFile("/codeInsight/generateConstructor/after" +
                      name +
                      ".java");

  }
}
