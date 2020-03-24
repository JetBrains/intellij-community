// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;
import org.jetbrains.annotations.NotNull;

public class JavaDocRenderTest extends AbstractEditorTest {
  public void testDeleteLine() {
    configureAndRender("class C {\n" +
                       "<caret>\n" +
                       "  /** doc */\n" +
                       "  int a;\n" +
                       "}\n");
    verifyFoldingState("[FoldRegion +(10:23), placeholder='']");
    executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
    checkResultByText("class C {\n" +
                      "<caret>  /** doc */\n" +
                      "  int a;\n" +
                      "}\n");
  }

  private void configureAndRender(@NotNull String text) {
    init(text, TestFileType.JAVA);
    DocRenderPassFactory.Items items = DocRenderPassFactory.calculateItemsToRender(getEditor().getDocument(), getFile());
    DocRenderPassFactory.applyItemsToRender(getEditor(), getProject(), items, true);
  }
}
