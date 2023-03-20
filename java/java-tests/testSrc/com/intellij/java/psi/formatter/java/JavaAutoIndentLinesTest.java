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
package com.intellij.java.psi.formatter.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class JavaAutoIndentLinesTest extends AbstractEditorTest {
  public void testSelection() {
    init("""
           class C {
           int <selection>a<caret></selection> = 1;
           }""",
         JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_AUTO_INDENT_LINES);
    checkResultByText("""
                        class C {
                            int <selection>a<caret></selection> = 1;
                        }""");
  }

  public void testKeepIndentsOnEmptyLinesWithSelection() {
    getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions().KEEP_INDENTS_ON_EMPTY_LINES = true;
    init(
      """
      public class Main {
          public static void main(String[] args) {
              <selection>if (args.length > 2) {
             \s
             \s
                  System.out.println("Too many args");
              }</selection>
          }
      }
      """,
      JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_AUTO_INDENT_LINES);
    checkResultByText(
      """
      public class Main {
          public static void main(String[] args) {
              if (args.length > 2) {
                 \s
                 \s
                  System.out.println("Too many args");
              }
          }
      }
      """);
  }
}
