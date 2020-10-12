// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.Interval;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.TestFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

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

  public void testExpandAll() {
    boolean savedValue = CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
    try {
      CodeFoldingSettings.getInstance().COLLAPSE_METHODS = true;
      configure("/** class */\n" +
              "class C {\n" +
              "  void m() {\n" +
              "  }\n" +
              "}", true);
      int methodBodyPos = getEditor().getDocument().getText().indexOf("{\n  }");
      CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(getEditor());
      executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
      assertNotNull(getEditor().getFoldingModel().getCollapsedRegionAtOffset(methodBodyPos));
      executeAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);
      assertNull(getEditor().getFoldingModel().getCollapsedRegionAtOffset(methodBodyPos));
    }
    finally {
      CodeFoldingSettings.getInstance().COLLAPSE_METHODS = savedValue;
    }
  }

  public void testTypingAfterCollapse() {
    configure("/**\n" +
              " * doc<caret>\n" +
              " */\n" +
              "class C {}", false);
    toggleItem();
    type("  ");
    checkResultByText("/**\n" +
                      " * doc\n" +
                      " */\n" +
                      "  <caret>class C {}");
  }

  public void testAddedCommentIsNotCollapsed() {
    configure("class C {}", true);
    runWriteCommand(() -> getEditor().getDocument().insertString(0, "/**\n * comment\n */\n"));
    updateRenderedItems(false);
    verifyFoldingState("[]");
  }

  public void testLineToYAndBackConversions() {
    configure("class C {\n" +
              "  /**\n" +
              "   * comment\n" +
              "   */\n" +
              "  void m() {}\n" +
              "}", true);
    List<Inlay<?>> inlays = getEditor().getInlayModel().getBlockElementsForVisualLine(1, true);
    assertSize(1, inlays);
    Rectangle inlayBounds = inlays.get(0).getBounds();
    assertNotNull(inlayBounds);
    assertFalse(inlayBounds.isEmpty());

    @NotNull Pair<@NotNull Interval, @Nullable Interval> p = EditorUtil.logicalLineToYRange(getEditor(), 2);
    assertEquals(inlayBounds.y, p.first.intervalStart());
    assertEquals(inlayBounds.y + inlayBounds.height, p.first.intervalEnd());
    assertNull(p.second);

    Interval lineRange = EditorUtil.yToLogicalLineRange(getEditor(), inlayBounds.y + inlayBounds.height / 2);
    assertEquals(1, lineRange.intervalStart());
    assertEquals(3, lineRange.intervalEnd());
  }

  public void testCommentAnnotationAfterDoc() {
    configure("/**\n" +
              " * doc\n" +
              " */\n" +
              "<caret>@Deprecated\n" +
              "class C {}", true);
    executeAction(IdeActions.ACTION_COMMENT_LINE);
    checkResultByText("/**\n" +
                      " * doc\n" +
                      " */\n" +
                      "//@Deprecated\n" +
                      "class C {}");
  }

  private void configure(@NotNull String text, boolean enableRendering) {
    EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(enableRendering);
    init(text, TestFileType.JAVA);
    updateRenderedItems(enableRendering);
  }

  private void updateRenderedItems(boolean collapseNewRegions) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
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
