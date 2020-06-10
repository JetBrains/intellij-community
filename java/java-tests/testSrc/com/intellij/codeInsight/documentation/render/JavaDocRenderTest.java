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
    verifyFoldingState("[FoldRegion +(11:24), placeholder='']");
    executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
    checkResultByText("class C {\n" +
                      "  /** doc */\n" +
                      "<caret>  int a;\n" +
                      "}\n");
  }

  public void testTypingAtLineStart() {
    configure("class C {\n" +
              "/** doc */\n" +
              "int a;<caret>\n" +
              "}\n", true);
    verifyFoldingState("[FoldRegion +(10:21), placeholder='']");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
    type(' ');
    checkResultByText("class C {\n" +
                      "/** doc */\n" +
                      " <caret>int a;\n" +
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
    verifyFoldingState("[FoldRegion +(10:23), placeholder='']");
    verifyItem(12, 22, "doc");
    toggleItem();
    verifyFoldingState("[]");
    verifyItem(12, 22, null);
    runWriteCommand(() -> getEditor().getDocument().setText(getEditor().getDocument().getText().replace("doc", "another")));
    toggleItem();
    verifyFoldingState("[FoldRegion +(10:27), placeholder='']");
    verifyItem(12, 26, "another");
  }

  public void testMultipleAuthors() {
    configure("package some;\n" +
              "\n" +
              "/**\n" +
              " * @author foo\n" +
              " * @author bar\n" +
              " */\n" +
              "class C {}", true);
    verifyItem(15, 52,"<table class='sections'><p><tr><td valign='top' class='section'><p>Author:</td>" +
                      "<td valign='top'><p>foo, bar</td></table>");
  }

  public void testDocumentStart() {
    configure("/**\n" +
              " * comment\n" +
              " */\n" +
              "class C {}", true);
    verifyFoldingState("[FoldRegion +(0:19), placeholder='']");
  }

  public void testPackageInfo() {
    EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(true);
    configureFromFileText("package-info.java",
                          "/**\n" +
                          " * whatever\n" +
                          " */\n" +
                          "package some;");
    updateRenderedItems(true);
    verifyItem(0, 19, "whatever");
  }

  public void testModuleInfo() {
    EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(true);
    configureFromFileText("module-info.java",
                          "/**\n" +
                          " * whatever\n" +
                          " */\n" +
                          "module some {}");
    updateRenderedItems(true);
    verifyItem(0, 19, "whatever");
  }

  public void testToggleNestedMember() {
    configure("/**\n" +
              " * class\n" +
              " */\n" +
              "class C {\n" +
              "  /**\n" +
              "   * method\n" +
              "   */\n" +
              "  void m() {\n" +
              "    <caret>\n" +
              "  }\n" +
              "}", false);
    verifyFoldingState("[]");
    toggleItem();
    verifyFoldingState("[FoldRegion +(27:51), placeholder='']");
  }

  private void configure(@NotNull String text, boolean enableRendering) {
    EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(enableRendering);
    init(text, TestFileType.JAVA);
    updateRenderedItems(enableRendering);
  }

  private void updateRenderedItems(boolean collapseNewRegions) {
    DocRenderPassFactory.Items items = DocRenderPassFactory.calculateItemsToRender(getEditor(), getFile());
    DocRenderPassFactory.applyItemsToRender(getEditor(), getProject(), items, collapseNewRegions);
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
