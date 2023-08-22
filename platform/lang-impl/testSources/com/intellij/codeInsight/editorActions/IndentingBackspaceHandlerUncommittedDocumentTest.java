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

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class IndentingBackspaceHandlerUncommittedDocumentTest extends LightPlatformCodeInsightTestCase {
  public void testSequentialBackspaceInvocation() {
    configureFromFileText(getTestName(false) + ".java",
                          """
                            class Foo {


                            <caret>}""");
    backspace();
    backspace();
    checkResultByText("class Foo {\n" +
                      "<caret>}");
  }

  public void testMulticaretSequentialBackspaceInvocation() {
    configureFromFileText(getTestName(false) + ".java",
                          """
                            class Foo {
                                void m1() {
                               \s
                                <caret>}
                                void m2() {
                               \s
                                <caret>}
                            }""");
    backspace();
    backspace();
    checkResultByText("""
                        class Foo {
                            void m1() {<caret>}
                            void m2() {<caret>}
                        }""");
  }
}