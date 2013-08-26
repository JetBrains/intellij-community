/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.util.List;

/**
 * @author ven
 */
public class GenerateConstructorTest extends LightCodeInsightTestCase {
  public void testAbstractClass() throws Exception { doTest(); }
  public void testPackageLocalClass() throws Exception { doTest(); }
  public void testPrivateClass() throws Exception { doTest(); }
  public void testBoundComments() throws Exception { doTest(); }
  public void testSameNamedFields() throws Exception { doTest(); }
  public void testEnumWithAbstractMethod() throws Exception { doTest(); }
  public void testNoMoreConstructorsCanBeGenerated() throws Exception { doTest(); }

  public void testImmediatelyAfterRBrace() throws Exception {    // IDEADEV-28811
    CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  public void testBoundCommentsKeepsBlankLine() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().BLANK_LINES_AFTER_CLASS_HEADER = 1;
    doTest();
  }

  public void testFinalFieldPreselection() throws Exception { doTest(true); }
  public void testSubstitution() throws Exception { doTest(true); }

  public void testFieldPrefixCoincidence() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().FIELD_NAME_PREFIX = "m";
    doTest();
  }

  public void testFieldPrefixCoincidence1() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().FIELD_NAME_PREFIX = "_";
    doTest();
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean preSelect) throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/generateConstructor/before" + name + ".java");
    new GenerateConstructorHandler() {
      @Override
      protected ClassMember[] chooseMembers(ClassMember[] members,
                                            boolean allowEmptySelection,
                                            boolean copyJavadocCheckbox,
                                            Project project, Editor editor) {
        if (preSelect) {
          final List<ClassMember> preselection = GenerateConstructorHandler.preselect(members);
          return preselection.toArray(new ClassMember[preselection.size()]);
        }
        else {
          return members;
        }
      }
    }.invoke(getProject(), getEditor(), getFile());
    checkResultByFile("/codeInsight/generateConstructor/after" + name + ".java");
  }
}
