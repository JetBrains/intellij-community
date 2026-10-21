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

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class JavaAutoIndentLinesTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
    CodeStyle.setTemporarySettings(myFixture.getProject(), CodeStyle.createTestSettings());
  }

  @AfterEach
  void tearDown() {
    CodeStyle.dropTemporarySettings(myFixture.getProject());
  }

  private CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyle.getSettings(myFixture.getProject());
  }

  @Test
  public void testSelection() {
    myFixture.runTest(() -> {
      myFixture.init("""
             class C {
             int <selection>a<caret></selection> = 1;
             }""",
           JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_AUTO_INDENT_LINES);
      myFixture.checkResultByText("""
                          class C {
                              int <selection>a<caret></selection> = 1;
                          }""");
    });
  }

  @Test
  public void testKeepIndentsOnEmptyLinesWithSelection() {
    myFixture.runTest(() -> {
      getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions().KEEP_INDENTS_ON_EMPTY_LINES = true;
      myFixture.init(
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
      myFixture.executeAction(IdeActions.ACTION_EDITOR_AUTO_INDENT_LINES);
      myFixture.checkResultByText(
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
    });
  }
}
