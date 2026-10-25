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
package com.intellij.codeInsight.editorActions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;

@TestApplication
public class IndentingBackspaceHandlerUncommittedDocumentTest {
  @SuppressWarnings("deprecation")
  private final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<String> testName = testNameFixture(false);

  @Test
  public void testSequentialBackspaceInvocation() {
    codeInsightFixture.get().configureByText(testName.get() + ".java",
                          """
                            class Foo {


                            <caret>}""");
    codeInsightFixture.get().performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    codeInsightFixture.get().performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    codeInsightFixture.get().checkResult("class Foo {\n" +
                      "<caret>}");
  }

  @Test
  public void testMulticaretSequentialBackspaceInvocation() {
    codeInsightFixture.get().configureByText(testName.get() + ".java",
                          """
                            class Foo {
                                void m1() {
                               \s
                                <caret>}
                                void m2() {
                               \s
                                <caret>}
                            }""");
    codeInsightFixture.get().performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    codeInsightFixture.get().performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    codeInsightFixture.get().checkResult("""
                        class Foo {
                            void m1() {<caret>}
                            void m2() {<caret>}
                        }""");
  }
}
