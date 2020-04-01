// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDocRenderTest extends AbstractEditorTest {
  private boolean myStoredSetting;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStoredSetting = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(myStoredSetting);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testDeleteLine() {
    configure("class C {\n" +
              "<caret>\n" +
              "  /** doc */\n" +
              "  int a;\n" +
              "}\n", true);
    verifyFoldingState("[FoldRegion +(10:23), placeholder='']");
    executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
    checkResultByText("class C {\n" +
                      "<caret>  /** doc */\n" +
                      "  int a;\n" +
                      "}\n");
  }

  public void testCommentModification() {
    configure("class C {\n" +
              "  /** doc */\n" +
              "  int a;<caret>\n" +
              "}\n", false);
    verifyFoldingState("[]");
    verifyItem(12, 22, null);
    toggleItem();
    verifyFoldingState("[FoldRegion +(9:22), placeholder='']");
    verifyItem(12, 22, "doc");
    toggleItem();
    verifyFoldingState("[]");
    verifyItem(12, 22, null);
    runWriteCommand(() -> getEditor().getDocument().setText(getEditor().getDocument().getText().replace("doc", "another")));
    toggleItem();
    verifyFoldingState("[FoldRegion +(9:26), placeholder='']");
    verifyItem(12, 26, "another");
  }

  private void configure(@NotNull String text, boolean enableRendering) {
    EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(enableRendering);
    init(text, TestFileType.JAVA);
    DocRenderPassFactory.Items items = DocRenderPassFactory.calculateItemsToRender(getEditor().getDocument(), getFile());
    DocRenderPassFactory.applyItemsToRender(getEditor(), getProject(), items, enableRendering);
  }

  private void toggleItem() {
    executeAction(IdeActions.ACTION_TOGGLE_RENDERED_DOC);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

  private void verifyItem(int startOffset, int endOffset, @Nullable String textInContent) {
    DocRenderItem item = DocRenderItem.getItemAroundOffset(getEditor(), startOffset);
    assertNotNull("Item is not found at offset " + startOffset, item);
    assertEquals("Unexpected item start offset", startOffset, item.highlighter.getStartOffset());
    assertEquals("Unexpected item end offset", endOffset, item.highlighter.getEndOffset());
    if (textInContent == null) {
      assertNull("Unexpected inlay", item.inlay);
    }
    else {
      assertNotNull("Inlay doesn't exist", item.inlay);
      assertTrue("Unexpected rendered text: " + item.textToRender, item.textToRender != null && item.textToRender.contains(textInContent));
    }
  }
}
