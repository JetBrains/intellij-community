/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ven
 */
public class GenerateConstructorTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/generateConstructor";
  }

  public void testAbstractClass() { doTest(); }
  public void testNewLine() { doTest(); }
  public void testPackageLocalClass() { doTest(); }
  public void testPrivateClass() { doTest(); }
  public void testBoundComments() { doTest(); }
  public void testSameNamedFields() { doTest(); }
  public void testEnumWithAbstractMethod() { doTest(); }
  public void testNoMoreConstructorsCanBeGenerated() { doTest(); }
  public void testBaseVarargs() { doTest(); }
  public void testFinalFieldPreselection() { doTest(true); }
  public void testSubstitution() { doTest(true); }

  public void testImmediatelyAfterRBrace() {    // IDEADEV-28811
    CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  public void testBoundCommentsKeepsBlankLine() {
    CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().getCommonSettings(JavaLanguage.INSTANCE).BLANK_LINES_AFTER_CLASS_HEADER = 1;
    doTest();
  }

  public void testFieldPrefixCoincidence() {
    getJavaSettings().FIELD_NAME_PREFIX = "m";
    doTest();
  }

  @NotNull
  private JavaCodeStyleSettings getJavaSettings() {
    return CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class);
  }

  public void testFieldPrefixCoincidence1() {
    getJavaSettings().FIELD_NAME_PREFIX = "_";
    doTest();
  }

  public void testTypeAnnotatedField() {
    myFixture.addClass("package foo;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.TYPE_USE) public @interface TestNotNull { }");
    NullableNotNullManager manager = NullableNotNullManager.getInstance(getProject());
    manager.setNotNulls("foo.TestNotNull");
    Disposer.register(myFixture.getTestRootDisposable(), manager::setNotNulls);
    doTest();
  }

  public void testNullableField() { doTest(); }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean preSelect) {
    String name = getTestName(false);
    myFixture.configureByFile("before" + name + ".java");
    new GenerateConstructorHandler() {
      @Override
      protected ClassMember[] chooseMembers(ClassMember[] members, boolean allowEmpty, boolean copyJavadoc, Project project, Editor editor) {
        if (preSelect) {
          List<ClassMember> preselection = GenerateConstructorHandler.preselect(members);
          return preselection.toArray(new ClassMember[preselection.size()]);
        }
        else {
          return members;
        }
      }
    }.invoke(getProject(), getEditor(), getFile());
    myFixture.checkResultByFile("after" + name + ".java");
  }
}