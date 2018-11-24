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
package com.intellij.java.codeInsight.folding;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInsight.folding.impl.JavaCodeFoldingSettingsImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public abstract class JavaFoldingTestCase extends LightCodeInsightFixtureTestCase {
  protected JavaCodeFoldingSettings myFoldingSettings;
  private JavaCodeFoldingSettings myFoldingSettingsBackup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFoldingSettings = JavaCodeFoldingSettings.getInstance();
    myFoldingSettingsBackup = new JavaCodeFoldingSettingsImpl();
    ((JavaCodeFoldingSettingsImpl)myFoldingSettingsBackup).loadState((JavaCodeFoldingSettingsImpl)myFoldingSettings);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ((JavaCodeFoldingSettingsImpl)myFoldingSettings).loadState(((JavaCodeFoldingSettingsImpl)myFoldingSettingsBackup));
    }
    finally {
      super.tearDown();
    }
  }

  protected void configure(String text) {
    myFixture.configureByText("a.java", text);
    performInitialFolding(myFixture.getEditor());
    myFixture.doHighlighting();
  }

  public static void performInitialFolding(Editor editor) {
    CodeFoldingManager.getInstance(editor.getProject()).buildInitialFoldings(editor);
    ((FoldingModelEx)editor.getFoldingModel()).rebuild();
  }
}