// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.documentation.render;

import com.intellij.codeInsight.documentation.render.DocRenderItem;
import com.intellij.codeInsight.documentation.render.DocRenderItemManager;
import com.intellij.codeInsight.documentation.render.DocRenderPassFactory;
import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.editor.impl.Interval;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaDocRenderTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private boolean myStoredSetting;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
    myStoredSetting = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled();
  }

  @AfterEach
  void tearDown() {
    EdtTestUtil.runInEdtAndWait(() -> EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(myStoredSetting));
  }

  @Test
  public void testDeleteLine() {
    myFixture.runTest(() -> {
      configure("""
                  class C {
                  <caret>
                    /** doc */
                    int a;
                  }
                  """, true);
      verifyFoldingState(11, 23);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
      myFixture.checkResultByText("""
                          class C {
                          <caret>  /** doc */
                            int a;
                          }
                          """);
    });
  }

  @Test
  public void testTypingAtLineStart() {
    myFixture.runTest(() -> {
      configure("""
                  class C {
                  /** doc */
                  int a;<caret>
                  }
                  """, true);
      verifyFoldingState(10, 20);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
      myFixture.type(' ');
      myFixture.checkResultByText("""
                          class C {
                          /** doc */
                           <caret>int a;
                          }
                          """);
    });
  }

  @Test
  public void testCommentModification() {
    myFixture.runTest(() -> {
      configure("""
                  class C {
                    /** doc */
                    int a;<caret>
                  }
                  """, false);
      verifyFoldingState();
      verifyItem(12, 22, null);
      toggleItem();
      verifyFoldingState(10, 22);
      verifyItem(12, 22, "doc");
      toggleItem();
      verifyFoldingState();
      verifyItem(12, 22, null);
      myFixture.runWriteCommand(
        () -> myFixture.getEditor().getDocument().setText(myFixture.getEditor().getDocument().getText().replace("doc", "another")));
      toggleItem();
      verifyFoldingState(10, 26);
      verifyItem(12, 26, "another");
    });
  }

  @Test
  public void testMultipleAuthors() {
    myFixture.runTest(() -> {
      configure("""
                  package some;

                  /**
                   * @author foo
                   * @author bar
                   */
                  class C {}""", true);
      verifyItem(15, 52,"<table class=\"sections\"><tbody><tr><td valign=\"top\" class=\"section\"><p>Author:</p></td>" +
                        "<td valign=\"top\"><p>foo, bar</p></td></tr></tbody></table>");
    });
  }

  @Test
  public void testDocumentStart() {
    myFixture.runTest(() -> {
      configure("""
                  /**
                   * comment
                   */
                  class C {}""", true);
      verifyFoldingState(0, 18);
    });
  }

  @Test
  public void testPackageInfo() {
    myFixture.runTest(() -> {
      EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(true);
      myFixture.configureFromFileText("package-info.java",
                            """
                              /**
                               * whatever
                               */
                              package some;""");
      updateRenderedItems(true);
      verifyItem(0, 19, "whatever");
    });
  }

  @Test
  public void testModuleInfo() {
    myFixture.runTest(() -> {
      EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(true);
      myFixture.configureFromFileText("module-info.java",
                            """
                              /**
                               * whatever
                               */
                              module some {}""");
      updateRenderedItems(true);
      verifyItem(0, 19, "whatever");
    });
  }

  @Test
  public void testToggleNestedMember() {
    myFixture.runTest(() -> {
      configure("""
                  /**
                   * class
                   */
                  class C {
                    /**
                     * method
                     */
                    void m() {
                      <caret>
                    }
                  }""", false);
      verifyFoldingState();
      toggleItem();
      verifyFoldingState(27, 50);
    });
  }

  @Test
  public void testExpandAll() {
    myFixture.runTest(() -> {
      boolean savedValue = CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
      try {
        CodeFoldingSettings.getInstance().COLLAPSE_METHODS = true;
        configure("""
                    /** class */
                    class C {
                      void m() {
                      }
                    }""", true);
        int methodBodyPos = myFixture.getEditor().getDocument().getText().indexOf("{\n  }");
        EditorTestUtil.buildInitialFoldingsInBackground(myFixture.getEditor());
        myFixture.executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
        assertNotNull(myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(methodBodyPos));
        myFixture.executeAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);
        assertNull(myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(methodBodyPos));
      }
      finally {
        CodeFoldingSettings.getInstance().COLLAPSE_METHODS = savedValue;
      }
    });
  }

  @Test
  public void testTypingAfterCollapse() {
    myFixture.runTest(() -> {
      configure("""
                  /**
                   * doc<caret>
                   */
                  class C {}""", false);
      toggleItem();
      myFixture.type("  ");
      myFixture.checkResultByText("""
                            <caret>/**
                           * doc
                           */
                          class C {}""");
    });
  }

  @Test
  public void testAddedCommentIsNotCollapsed() {
    myFixture.runTest(() -> {
      configure("class C {}", true);
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().insertString(0, "/**\n * comment\n */\n"));
      updateRenderedItems(false);
      verifyFoldingState();
    });
  }

  @Test
  public void testLineToYAndBackConversions() {
    myFixture.runTest(() -> {
      configure("""
                  class C {
                    /**
                     * comment
                     */
                    void m() {}
                  }""", true);
      FoldRegion foldRegion = myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(10);
      assertInstanceOf(CustomFoldRegion.class, foldRegion);
      CustomFoldRegion cfr = (CustomFoldRegion)foldRegion;
      Point location = cfr.getLocation();
      assertNotNull(location);
      Rectangle rendererBounds = new Rectangle(location, new Dimension(cfr.getWidthInPixels(), cfr.getHeightInPixels()));
      assertFalse(rendererBounds.isEmpty());

      @NotNull Pair<@NotNull Interval, @Nullable Interval> p = EditorUtil.logicalLineToYRange(myFixture.getEditor(), 2);
      assertEquals(rendererBounds.y, p.first.intervalStart());
      assertEquals(rendererBounds.y + rendererBounds.height, p.first.intervalEnd());
      assertNull(p.second);

      Interval lineRange = EditorUtil.yToLogicalLineRange(myFixture.getEditor(), rendererBounds.y + rendererBounds.height / 2);
      assertEquals(1, lineRange.intervalStart());
      assertEquals(3, lineRange.intervalEnd());
    });
  }

  @Test
  public void testCommentAnnotationAfterDoc() {
    myFixture.runTest(() -> {
      configure("""
                  /**
                   * doc
                   */
                  <caret>@Deprecated
                  class C {}""", true);
      myFixture.executeAction(IdeActions.ACTION_COMMENT_LINE);
      myFixture.checkResultByText("""
                          /**
                           * doc
                           */
                          //@Deprecated
                          class C {}""");
    });
  }

  @Test
  public void testVisibleAreaChangeWhileScreenDevicesChange() {
    myFixture.runTest(() -> {
      configure("""
                  class C {
                    /**
                     * doc
                     */
                    int a;
                  }""", true);
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 1);
      EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
      boolean storedQuickDocOnMouseHover = editorSettings.isShowQuickDocOnMouseOverElement();
      // the mouse move below would schedule a hover documentation popup, which can outlive the test and its PSI
      editorSettings.setShowQuickDocOnMouseOverElement(false);
      try {
        // a headless EditorImpl reports a hidden cursor until the first mouse event, and the icon update skips a hidden cursor
        new EditorMouseFixture((EditorImpl)myFixture.getEditor()).moveToXY(0, 0);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        try (MockedStatic<MouseInfo> mouseInfo = Mockito.mockStatic(MouseInfo.class)) {
          mouseInfo.when(MouseInfo::getPointerInfo).thenThrow(new ArrayIndexOutOfBoundsException("Index 1 out of bounds for length 1"));
          EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 2);
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          mouseInfo.verify(MouseInfo::getPointerInfo, Mockito.atLeastOnce());
        }
      }
      finally {
        editorSettings.setShowQuickDocOnMouseOverElement(storedQuickDocOnMouseHover);
      }
    });
  }

  private void configure(@NotNull String text, boolean enableRendering) {
    EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(enableRendering);
    myFixture.init(text, JavaFileType.INSTANCE);
    updateRenderedItems(enableRendering);
  }

  private void updateRenderedItems(boolean collapseNewRegions) {
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    DocRenderPassFactory.Items items = DocRenderPassFactory.calculateItemsToRender(myFixture.getEditor(), myFixture.getFile());
    DocRenderPassFactory.applyItemsToRender(myFixture.getEditor(), myFixture.getProject(), items, collapseNewRegions);
  }

  private void toggleItem() {
    myFixture.executeAction(IdeActions.ACTION_TOGGLE_RENDERED_DOC);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

  private void verifyItem(int startOffset, int endOffset, @Nullable String textInContent) {
    DocRenderItem item = DocRenderItemManager.getInstance().getItemAroundOffset(myFixture.getEditor(), startOffset);
    assertNotNull(item, "Item is not found at offset " + startOffset);
    assertEquals(startOffset, item.getHighlighter().getStartOffset(), "Unexpected item start offset");
    assertEquals(endOffset, item.getHighlighter().getEndOffset(), "Unexpected item end offset");
    Object toCheck = item.getFoldRegion();
    if (textInContent == null) {
      assertNull(toCheck, "Item in rendered state");
    }
    else {
      assertNotNull(toCheck, "Item not in rendered state");
      assertTrue(item.getTextToRender() != null && item.getTextToRender().contains(textInContent), "Unexpected rendered text: " + item.getTextToRender());
    }
  }

  private void verifyFoldingState(int... collapsedRegionOffsets) {
    int expectedNumberOfCollapsedRegions = collapsedRegionOffsets.length / 2;
    List<FoldRegion> foldRegions =
      ContainerUtil.filter(myFixture.getEditor().getFoldingModel().getAllFoldRegions(), region -> !region.isExpanded());
    assertEquals(expectedNumberOfCollapsedRegions, foldRegions.size(), "Unexpected number of collapsed fold regions");
    for (int i = 0; i < expectedNumberOfCollapsedRegions; i++) {
      FoldRegion region = foldRegions.get(i);
      assertEquals(collapsedRegionOffsets[i * 2], region.getStartOffset(), "Unexpected region " + i + " start offset");
      assertEquals(collapsedRegionOffsets[i * 2 + 1], region.getEndOffset(), "Unexpected region " + i + " end offset");
    }
  }
}
