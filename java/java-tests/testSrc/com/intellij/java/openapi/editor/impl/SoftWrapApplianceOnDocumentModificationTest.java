// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.editor.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapParsingListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class SoftWrapApplianceOnDocumentModificationTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private boolean mySmartHome;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
    CodeStyle.setTemporarySettings(myFixture.getProject(), CodeStyle.createTestSettings());
    if (myFixture.getFixture().getEditor() == null) {
      return;
    }

    EditorSettings settings = myFixture.getEditor().getSettings();
    mySmartHome = settings.isSmartHome();
  }

  @AfterEach
  void tearDown() {
    try {
      EdtTestUtil.runInEdtAndWait(() -> {
        if (myFixture.getFixture().getEditor() != null) {
          EditorSettings settings = myFixture.getEditor().getSettings();
          settings.setUseSoftWraps(false);
          settings.setSmartHome(mySmartHome);
        }
      });
    }
    finally {
      CodeStyle.dropTemporarySettings(myFixture.getProject());
    }
  }

  private CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyle.getSettings(myFixture.getProject());
  }

  @Test
  public void testSoftWrapAdditionOnTyping() {
    myFixture.runTest(() -> {
      String text =
        "this is a test string that is expected to end just before right margin<caret>";
      init(100, text);

      int offset = myFixture.getEditor().getDocument().getTextLength() + 1;
      assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());
      myFixture.type(" thisisalongtokenthatisnotexpectedtobebrokenintopartsduringsoftwrapping");
      SoftWrap softWrap = myFixture.getEditor().getSoftWrapModel().getSoftWrap(offset);
    
      // This test fails randomly at TeamCity. The logging below is intended to help analyzing the problem.
      if (softWrap == null) {
        fail(String.format(
          "Expected soft wrap to be located on offset %d. Actual: %s. Document text: %s",
          offset, getSoftWrapModel().getRegisteredSoftWraps(), myFixture.getEditor().getDocument().getCharsSequence()
        ));
      }
    });
  }

  @Test
  public void testLongLineOfIdSymbolsIsSoftWrapped() {
    myFixture.runTest(() -> {
      String text =
        """
          abcdefghijklmnopqrstuvwxyz<caret>
          123
          ABCDEFGHIJKLMNOPQRSTUVWXYZ""";

      init(15, text);
      myFixture.verifySoftWrapPositions(14, 45);
      myFixture.type('1');
      myFixture.verifySoftWrapPositions(14, 46);
      myFixture.type(" test");
      myFixture.verifySoftWrapPositions(14, 27, 51);
    });
  }

  @Test
  public void testFoldRegionCollapsing() {
    myFixture.runTest(() -> {
      String text =
        """
          class Test {
              public void foo() {
                  System.out.println("test");
              }
          }""";

      init(40, text);
      final FoldingModel foldingModel = myFixture.getEditor().getFoldingModel();
      assertThat(foldingModel.getAllFoldRegions()).isEmpty();

      final int startOffset = text.indexOf('{');
      final int endOffset = text.indexOf('}') + 1;

      VisualPosition foldStartPosition = myFixture.getEditor().offsetToVisualPosition(startOffset);

      myFixture.addFoldRegion(startOffset, endOffset, "...");

      final FoldRegion foldRegion = myFixture.getFoldRegion(startOffset);
      assertNotNull(foldRegion);
      assertTrue(foldRegion.isExpanded());
      myFixture.toggleFoldRegionState(foldRegion, false);

      // Expecting that all offsets that belong to collapsed fold region point to the region's start.
      assertEquals(foldStartPosition, myFixture.getEditor().offsetToVisualPosition(startOffset + 5));
    });
  }

  @Test
  public void testTypingEnterAtDocumentEnd() {
    myFixture.runTest(() -> {
      String text =
        """
          class Test {
              public void foo() {
                  System.out.println("test");
              }
          }<caret>""";

      init(40, text);
      myFixture.type('\n');
      VisualPosition position = myFixture.getEditor().getCaretModel().getVisualPosition();
      assertEquals(new VisualPosition(5, 0), position);
    });
  }

  @Test
  public void testDeleteDocumentTail() {
    myFixture.runTest(() -> {
      String text =
        """
          class Test {
              public void foo() {
                  System.out.println("test");
              }
          }
          abcde""";

      init(40, text);
      int offset = text.indexOf("abcde");
      myFixture.getEditor().getSelectionModel().setSelection(offset, text.length());
      myFixture.delete();
      assertEquals(new VisualPosition(5, 0), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  @Test
  public void testTypingTabOnLastEmptyLine() {
    myFixture.runTest(() -> {
      String text =
        """
          class Test {
          }
          <caret>""";

      init(40, text);
      myFixture.type('\t');
      assertEquals(new VisualPosition(2, 4), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  @Test
  public void testTrailingFoldRegionRemoval() {
    myFixture.runTest(() -> {
      String text =
        """
          public class BrokenAlignment {
              @SuppressWarnings({ "SomeInspectionIWantToIgnore" })
              public void doSomething(int x, int y) {
              }
          }""";

      init(100, text);

      int startFoldOffset = text.indexOf('@');
      int endFoldOffset = text.indexOf(')');
      myFixture.addCollapsedFoldRegion(startFoldOffset, endFoldOffset, "/SomeInspectionIWantToIgnore/");

      int endSelectionOffset = text.lastIndexOf("}\n") + 1;
      myFixture.getEditor().getSelectionModel().setSelection(startFoldOffset, endSelectionOffset);

      myFixture.delete();
      // Don't expect any exceptions here.
    });
  }

  @Test
  public void testTypeNewLastLineAndSymbolOnIt() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-59439
      String text =
        """
          This is a test document
          line1
          line2
          line3
          line4
          line5
          line6<caret>""";
    
      init(100, text);
      myFixture.type("\nq");
      assertEquals(new VisualPosition(7, 1),
                   myFixture.getEditor().offsetToVisualPosition(myFixture.getEditor().getDocument().getTextLength()));
    });
  }
  
  @Test
  public void testTrailingSoftWrapOffsetShiftOnTyping() {
    myFixture.runTest(() -> {
      // The main idea is to type on a logical line before soft wrap in order to ensure that its offset is correctly shifted back.
      String text = 
        "line1<caret>\n" +
        "second line that is long enough to be soft wrapped";
      init(15, text);

      IntSet offsetsBefore = collectSoftWrapStartOffsets(1);
      assertFalse(offsetsBefore.isEmpty());
    
      myFixture.type('2');
      final IntSet offsetsAfter = collectSoftWrapStartOffsets(1);
      assertSame(offsetsBefore.size(), offsetsAfter.size());
      offsetsBefore.forEach(value -> {
        assertTrue(offsetsAfter.contains(value + 1));
      });
    });
  }
  
  @Test
  public void testSoftWrapAwareMappingAfterLeadingFoldRegionCollapsing() {
    myFixture.runTest(() -> {
      String text =
        """
          line to fold 1
          line to fold 2
          line to fold 3
          ordinary line 1
          ordinary line 2
          ordinary line 3
          ordinary line 4
          line that is long enough to be soft wrapped
          ordinary line 5
          ordinary line 6
          ordinary line 7
          ordinary line 8
          """;
    
      init(30, text);
      LogicalPosition position = myFixture.getEditor().visualToLogicalPosition(new VisualPosition(8, 0));
      assertSame(7, position.line); // Position from soft-wrapped part of the line
    
      myFixture.addCollapsedFoldRegion(0, text.indexOf("ordinary line 1") - 1, "...");
      // Check that soft wraps cache is correctly updated
      assertSame(7, myFixture.getEditor().visualToLogicalPosition(new VisualPosition(6, 0)).line);
    });
  }
  
  @Test
  public void testCaretPositionOnFoldRegionExpand() {
    myFixture.runTest(() -> {
      // We had a problem that caret preserved its visual position instead of offset. This test checks that.
    
      String text =
        """
          /**
           * This is a test comment
           */
          public class Test {
          }""";
      init(100, text);

      myFixture.addCollapsedFoldRegion(0, text.indexOf("public") - 1, "/**...*/");
    
      int offset = text.indexOf("class");
      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      caretModel.moveToOffset(offset);
      assertEquals(offset, caretModel.getOffset());
      assertEquals(1, caretModel.getVisualPosition().line);
    
      myFixture.toggleFoldRegionState(myFixture.getFoldRegion(0), true);
      assertEquals(3, caretModel.getVisualPosition().line);
      assertEquals(offset, caretModel.getOffset());
    });
  }
  
  @Test
  public void testBackspaceAtTheEndOfSoftWrappedLine() {
    myFixture.runTest(() -> {
      // There was a problem that removing text from the last document line that was soft-wrapped removed soft wraps as well.
      String text = 
        "This a long string that is expected to be wrapped in more than one visual line<caret>";
      init(20, text);

      List<SoftWrap> softWrapsBeforeModification = new ArrayList<>(getSoftWrapModel().getRegisteredSoftWraps());
      assertFalse(softWrapsBeforeModification.isEmpty());
    
      myFixture.backspace();
      assertEquals(softWrapsBeforeModification, getSoftWrapModel().getRegisteredSoftWraps());
    });
  }
  
  @Test
  public void testRemoveOfAllSymbolsFromLastLine() {
    myFixture.runTest(() -> {
      // There was a problem that removing all text from the last document line corrupted soft wraps cache.
      String text =
        "Line1\n" +
        "Long line2 that is expected to be soft-wrapped<caret>";
      init(20, text);

      List<SoftWrap> softWrapsBeforeModification = new ArrayList<>(getSoftWrapModel().getRegisteredSoftWraps());
      assertFalse(softWrapsBeforeModification.isEmpty());

      int offset = myFixture.getEditor().getCaretModel().getOffset();
      VisualPosition positionBeforeModification = myFixture.getEditor().offsetToVisualPosition(offset);
      myFixture.type("\n123");
      myFixture.getEditor().getSelectionModel().setSelection(offset + 1, myFixture.getEditor().getDocument().getTextLength());
      myFixture.delete();
      assertEquals(softWrapsBeforeModification, getSoftWrapModel().getRegisteredSoftWraps());
      assertEquals(positionBeforeModification, myFixture.getEditor().offsetToVisualPosition(offset));
    });
  }
  
  @Test
  public void testTypingBeforeCollapsedFoldRegion() {
    myFixture.runTest(() -> {
      // We had a problem that soft wraps cache entries that lay after the changed region were considered to be affected by the change.
      // This test checks that situation.
      String text =
        """

          fold line1
          fold line2
          fold line3
          fold line4
          normal line1""";
      init(20, text);
    
      int afterFoldOffset = text.indexOf("normal line1");
      myFixture.addCollapsedFoldRegion(text.indexOf("fold line1") + 2, afterFoldOffset - 1, "...");
      VisualPosition beforeModification = myFixture.getEditor().offsetToVisualPosition(afterFoldOffset);
    
      myFixture.getEditor().getCaretModel().moveToOffset(0);
      myFixture.type('a');
    
      assertEquals(beforeModification, myFixture.getEditor().offsetToVisualPosition(afterFoldOffset + 1));
    });
  }
  
  @Test
  public void testCollapsedFoldRegionPlaceholderThatExceedsVisibleWidth() {
    myFixture.runTest(() -> {
      String text =
        """
          line1
          line2
          line3
          line4
          line5
          this is long line that is expected to be soft-wrapped
          line6""";
      init(15, text);

      assertFalse(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());

      int offsetAfterSingleLineFoldRegion = text.indexOf("line2") - 1;
      int lineAfterSingleLineFoldRegion = myFixture.getEditor().offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line;

      int offsetAfterMultiLineFoldRegion = text.indexOf("this") - 1;
      int lineAfterMultiLineFoldRegion = myFixture.getEditor().offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line;

      int offsetAfterSoftWrap = text.indexOf("line6") - 1;
      VisualPosition beforePositionAfterSoftWrap = myFixture.getEditor().offsetToVisualPosition(offsetAfterSoftWrap);
    
      // Add single-line fold region which placeholder text is long enough to exceed visual area width.
      myFixture.addCollapsedFoldRegion(2, offsetAfterSingleLineFoldRegion,
                                       "this is a very long placeholder for the single-line fold region");
      assertEquals(lineAfterSingleLineFoldRegion + 1, myFixture.getEditor().offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line);
      lineAfterSingleLineFoldRegion++;
    
      assertEquals(lineAfterMultiLineFoldRegion + 1, myFixture.getEditor().offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line);
      lineAfterMultiLineFoldRegion++;
    
      beforePositionAfterSoftWrap = new VisualPosition(beforePositionAfterSoftWrap.line + 1, beforePositionAfterSoftWrap.column); 
      assertEquals(beforePositionAfterSoftWrap, myFixture.getEditor().offsetToVisualPosition(offsetAfterSoftWrap));
    
      // Add multi-line fold region which placeholder is also long enough to exceed visual area width.
      myFixture.addCollapsedFoldRegion(text.indexOf("line2") + 2, offsetAfterMultiLineFoldRegion,
                                       "long enough placeholder for multi-line fold region");
      assertEquals(lineAfterSingleLineFoldRegion, myFixture.getEditor().offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line);
      assertEquals(lineAfterMultiLineFoldRegion - 2, myFixture.getEditor().offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line);
      beforePositionAfterSoftWrap = new VisualPosition(beforePositionAfterSoftWrap.line - 2, beforePositionAfterSoftWrap.column);
      assertEquals(beforePositionAfterSoftWrap, myFixture.getEditor().offsetToVisualPosition(offsetAfterSoftWrap));
    });
  }
  
  @Test
  public void testInsertNewStringAndTypeOnItBeforeFoldRegion() {
    myFixture.runTest(() -> {
      // There was incorrect processing of fold regions when document change was performed right before them.
      String text =
        """
          /**
           * comment
           */
          class Test {
          }""";
      init(100, text);
      String placeholder = "/**...*/";
      myFixture.addCollapsedFoldRegion(0, text.indexOf("class") - 1, placeholder);
    
      myFixture.getEditor().getCaretModel().moveToOffset(0);
      myFixture.type("\n");
      myFixture.getEditor().getCaretModel().moveToOffset(0);
      myFixture.type("import");
    
      // Check that fold region info is still correct.
      int foldStartOffset = myFixture.getEditor().getDocument().getText().indexOf("/**");
      int foldEndOffset = myFixture.getEditor().getDocument().getText().indexOf("class") - 1;
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().offsetToVisualPosition(foldStartOffset));
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().offsetToVisualPosition((foldStartOffset + foldEndOffset) / 2));
      assertEquals(new VisualPosition(1, placeholder.length()), myFixture.getEditor().offsetToVisualPosition(foldEndOffset));
    });
  }
  
  @Test
  public void testUpdateFoldRegionDataOnTextRemoveBeforeIt() {
    myFixture.runTest(() -> {
      String text =
        """
          1
          /**
           * comment
           */
          class Test {
          }""";
      init(100, text);
      String placeholder = "/**...*/";
      myFixture.addCollapsedFoldRegion(2, text.indexOf("class") - 1, placeholder);
    
      myFixture.getEditor().getCaretModel().moveToOffset(0);
      myFixture.delete();
      assertEquals(new VisualPosition(1, placeholder.length()), myFixture.getEditor().offsetToVisualPosition(text.indexOf("class") - 2));
    });
  }
  
  @Test
  public void testRemoveCollapsedFoldRegionThatStartsLogicalLine() {
    myFixture.runTest(() -> {
      // There was a problem that soft wraps cache updated on document modification didn't contain information about removed
      // fold region but fold model still provided cached information about it.
      String text =
        """
          package org;

          @SuppressWarnings("all")
          class Test {
          }""";
    
      init(100, text);
      int startOffset = text.indexOf("@");
      int endOffset = text.indexOf("class") - 1;
      myFixture.addCollapsedFoldRegion(startOffset, endOffset, "xxx");
    
      // Delete collapsed fold region that starts logical line.
      myFixture.getEditor().getSelectionModel().setSelection(startOffset, endOffset);
      myFixture.delete();
    
      assertEquals(startOffset,
                   myFixture.getEditor().logicalPositionToOffset(myFixture.getEditor().visualToLogicalPosition(new VisualPosition(2, 0))));
    });
  }
  
  @Test
  public void testFoldRegionThatStartsAtLineEnd() {
    myFixture.runTest(() -> {
      String text =
        """
          line1
          line2
          line3
          line4
          line5""";
    
      init(63, text, 10);
      int start = text.indexOf("line3") - 1;
      myFixture.addCollapsedFoldRegion(start, text.length(), "...");
      assertEquals(1, getSoftWrapModel().getRegisteredSoftWraps().size());
      assertEquals(start, getSoftWrapModel().getRegisteredSoftWraps().getFirst().getStart());
    });
  }
  
  @Test
  public void testHomeProcessing() {
    myFixture.runTest(() -> {
      String text =
        """
          class Test {
              public String s = "this is a long string literal that is expected to be soft-wrapped into multiple visual lines";
          }""";
    
      init(30, text, false);
      myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("}") - 1);

      checkSoftWraps(41, 66, 89, 116);

      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      myFixture.home();
      assertEquals(116, caretModel.getOffset());
      assertEquals(new VisualPosition(5, 1), caretModel.getVisualPosition());
      myFixture.home();
      assertEquals(116, caretModel.getOffset());
      assertEquals(new VisualPosition(5, 0), caretModel.getVisualPosition());
    
      // Expecting caret to be located on the first non-white space symbol of non-soft wrapped line.
      myFixture.home();
      assertEquals(text.indexOf("public"), caretModel.getOffset());
      assertEquals(new VisualPosition(1, 4), caretModel.getVisualPosition());
    });
  }

  @Test
  public void testCustomWrap() {
    myFixture.runTest(() -> {
      String text =
        """
          class Test {
              public String s = "this is a long string literal that is expected to be soft-wrapped into multiple visual lines";
          }""";

      init(30, text, true);
      checkSoftWraps(41, 65, 89, 107);
    });
  }

  @Test
  public void testEndProcessing() {
    myFixture.runTest(() -> {
      String text =
        """
          class Test {
              public String s = "this is a long string literal that is expected to be soft-wrapped into multiple visual lines";  \s
          }""";

      init(30, text, false);
      myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("\n") + 1);

      checkSoftWraps(41, 66, 89, 116);

      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      myFixture.end();
      assertEquals(40, caretModel.getOffset());
      assertEquals(new VisualPosition(1, 27), caretModel.getVisualPosition());
      myFixture.end();
      assertEquals(41, caretModel.getOffset());
      assertEquals(new VisualPosition(1, 28), caretModel.getVisualPosition());
    
      // Check that caret is placed on a last non-white space symbol on current logical line.
      myFixture.end();
      int lastNonWhiteSpaceSymbolOffset = text.indexOf("\";") + 2;
      assertEquals(lastNonWhiteSpaceSymbolOffset, caretModel.getOffset());
      assertEquals(myFixture.getEditor().offsetToVisualPosition(lastNonWhiteSpaceSymbolOffset), caretModel.getVisualPosition());
    
      // Check that caret is place to the very end of the logical line.
      myFixture.end();
      int lastSymbolOffset = myFixture.getEditor().getDocument().getLineEndOffset(caretModel.getLogicalPosition().line);
      assertEquals(lastSymbolOffset, caretModel.getOffset());
      assertEquals(myFixture.getEditor().offsetToVisualPosition(lastSymbolOffset), caretModel.getVisualPosition());
    });
  }
  
  @Test
  public void testSoftWrapToHardWrapConversion() {
    myFixture.runTest(() -> {
      String text =
        """
          this is line 1
          this is line 2
          this is line 3
          this is line 4
          this is line 5""";
    
      init(71, text, 10);
      VisualPosition changePosition = new VisualPosition(1, 0);
      myFixture.getEditor().getCaretModel().moveToVisualPosition(changePosition);
    
      int logicalLinesBefore = myFixture.getEditor().offsetToLogicalPosition(text.length()).line;
      int offsetBefore = myFixture.getEditor().getCaretModel().getOffset();
    
      LogicalPosition logicalPositionBefore = myFixture.getEditor().visualToLogicalPosition(changePosition);
      assertEquals(1, EditorUtil.getSoftWrapCountAfterLineStart(myFixture.getEditor(), logicalPositionBefore));
      assertTrue(logicalPositionBefore.column > 0);
    
      SoftWrap softWrap = getSoftWrapModel().getSoftWrap(offsetBefore);
      assertNotNull(softWrap);
    
      myFixture.type('a');

      LogicalPosition logicalPositionAfter = myFixture.getEditor().visualToLogicalPosition(changePosition);
      assertEquals(new LogicalPosition(1, 0), logicalPositionAfter);
      assertEquals(offsetBefore + softWrap.getText().length() + 1, myFixture.getEditor().getCaretModel().getOffset());
      assertEquals(logicalLinesBefore + 1, myFixture.getEditor().offsetToLogicalPosition(text.length()).line);
    });
  }
  
  @Test
  public void testPastingInsideSelection() {
    myFixture.runTest(() -> {
      String text =
        """
          this is line number 0
          this is line number 1
          this is line number 2
          this is line number 3
          this is line number 4
          this is line number 5
          this is line number 6
          this is the last line""";
    
      init(100, text);
      int lineToSelect = 4;
      myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("number " + lineToSelect));
      Document document = myFixture.getEditor().getDocument();
  
      int startOffset = document.getLineStartOffset(lineToSelect);
      int endOffset = document.getLineEndOffset(lineToSelect);
      myFixture.getEditor().getSelectionModel().setSelection(startOffset, endOffset);
    
      VisualPosition positionBefore = myFixture.getEditor().offsetToVisualPosition(document.getLineStartOffset(lineToSelect + 1));
      List<SoftWrap> softWrapsBefore = new ArrayList<>(getSoftWrapModel().getRegisteredSoftWraps());
    
      myFixture.copy();
      myFixture.paste();
    
      assertEquals(positionBefore, myFixture.getEditor().offsetToVisualPosition(document.getLineStartOffset(lineToSelect + 1)));
      assertEquals(softWrapsBefore, getSoftWrapModel().getRegisteredSoftWraps());
    });
  }
  
  @Test
  public void testRemoveHugeLogicalLineThatLaysBeforeSoftWrappedLines() {
    myFixture.runTest(() -> {
      String text =
        """
          short line
          this is a long line that is expected to be soft wrapped into more than one or even two visual lines
          1. just a line that is long enough to be soft wrapped
          2. just a line that is long enough to be soft wrapped
          3. just a line that is long enough to be soft wrapped
          4. just a line that is long enough to be soft wrapped""";
    
      init(15, text);
      Document document = myFixture.getEditor().getDocument();
      int start = document.getLineStartOffset(1);
      int end = document.getLineEndOffset(1) + 1;
      int visualLinesToRemove = getSoftWrapModel().getSoftWrapsForLine(1).size() + 1;
    
      List<VisualPosition> positionsBefore = new ArrayList<>();
      for (int i = end; i < text.length(); i++) {
        positionsBefore.add(myFixture.getEditor().offsetToVisualPosition(i));
      }
      Collections.reverse(positionsBefore);

      myFixture.getEditor().getSelectionModel().setSelection(start, end);
      myFixture.delete();
    
      // Check that all remembered positions are just shifted to expected number of visual lines.
      for (int i = start; i < document.getTextLength(); i++) {
        VisualPosition position = positionsBefore.removeLast();
        assertEquals(new VisualPosition(position.line - visualLinesToRemove, position.column),
                     myFixture.getEditor().offsetToVisualPosition(i));
      }
    });
  }
  
  @Test
  public void testVerticalCaretShiftOnLineComment() {
    myFixture.runTest(() -> {
      String text =
        """
          1. just a line that is long enough to be soft wrapped
          2. just a line that is long enough to be soft wrapped
          3. just a line that is long enough to be soft wrapped
          4. just a line that is long enough to be soft wrapped""";
      init(15, text);

      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      caretModel.moveToOffset(text.indexOf("2.") + 2);
      new CommentByLineCommentAction().actionPerformedImpl(myFixture.getProject(), myFixture.getEditor());
    
      assertEquals(myFixture.getEditor().offsetToLogicalPosition(text.indexOf("3.") + 2), caretModel.getLogicalPosition());
    });
  }
  
  private IntSet collectSoftWrapStartOffsets(int documentLine) {
    IntSet result = new IntOpenHashSet();
    for (SoftWrap softWrap : myFixture.getEditor().getSoftWrapModel().getSoftWrapsForLine(documentLine)) {
      result.add(softWrap.getStart());
    }
    return result;
  }
  
  @Test
  public void testNonSmartHome() {
    myFixture.runTest(() -> {
      String text =
        """
               this is a string that starts with white space and is long enough to be soft-wrapped
           this is a 'prefix' text before collapsed multi-line folding that is long enough to be soft-wrapped first fold line
          second fold line""";
      init(30, text);
    
      myFixture.addCollapsedFoldRegion(text.indexOf("first fold line"), text.length(), "...");

      List<? extends SoftWrap> softWraps = getSoftWrapModel().getRegisteredSoftWraps();
      assertFalse(softWraps.isEmpty());

      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      SoftWrap softWrap = softWraps.getFirst();
    
      // Test non-smart home
      myFixture.getEditor().getSettings().setSmartHome(false);
      caretModel.moveToOffset(softWrap.getStart() + 1);
      int visLine = caretModel.getVisualPosition().line;
      myFixture.home();
      assertEquals(new VisualPosition(visLine, 0), caretModel.getVisualPosition());
    
      caretModel.moveToOffset(text.length());
      myFixture.home();
      visLine = caretModel.getVisualPosition().line;
      assertEquals(new VisualPosition(visLine, 0), caretModel.getVisualPosition());
    });
  }
  
  @Test
  public void testFoldRegionsUpdate() {
    myFixture.runTest(() -> {
      String text =
        """
          import java.util.List;
          import java.util.ArrayList;

          class Test {
          }""";
      init(40, text);
    
      final int foldStartOffset = "import".length() + 1;
      int foldEndOffset = text.indexOf("class") - 2;
      myFixture.addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...");
    
      // Simulate addition of the new import that modifies existing fold region.
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> myFixture.getEditor()
        .getDocument().insertString(foldEndOffset, "\nimport java.util.Date;\n"));

      final FoldingModel foldingModel = myFixture.getEditor().getFoldingModel();
      foldingModel.runBatchFoldingOperation(() -> {
        FoldRegion oldFoldRegion = myFixture.getFoldRegion(foldStartOffset);
        assertNotNull(oldFoldRegion);
        foldingModel.removeFoldRegion(oldFoldRegion);
      
        int newFoldEndOffset = myFixture.getEditor().getDocument().getText().indexOf("class") - 2;
        FoldRegion newFoldRegion = foldingModel.addFoldRegion(foldStartOffset, newFoldEndOffset, "...");
        assertNotNull(newFoldRegion);
        newFoldRegion.setExpanded(false);
      });
      CodeFoldingManager.getInstance(myFixture.getProject()).updateFoldRegions(myFixture.getEditor());
      assertEquals(new VisualPosition(2, 0), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(5, 0)));
    });
  }

  @Test
  public void testModificationOfSoftWrappedFoldRegion() {
    myFixture.runTest(() -> {
      String text =
        """
          import java.util.List;import java.util.ArrayList;import java.util.Collection;import java.util.Collections;
          import java.util.LinkedList;
          import java.util.Set;

          class Test {
          }""";
      init(40, text);

      final int foldStartOffset = text.indexOf("java.util.Collections");
      final int foldEndOffset = text.indexOf("class") - 2;
      myFixture.addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...");

      int modificationOffset = text.indexOf("java.util.Set");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> myFixture.getEditor()
        .getDocument().insertString(modificationOffset, "import java.util.HashSet;\n"));

      // Used to get StackOverflowError here, hence, no additional checking is performed.
    });
  }

  @Test
  public void testLongSoftWrappedLineWithNonWrappedEndInTheMiddleOfDocument() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-70114
    
      String text =
        """
          111
          222
          33333333 33333333333333333333333333333333
          444""";
      init(15, text);
    
      assertEquals(new LogicalPosition(3, 0), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(6, 0)));
    });
  }
  
  @Test
  public void testDeleteThatEndsOnLineWithMultiLineFoldRegion() {
    myFixture.runTest(() -> {
      String text =
        """
          111
          222
          333
          444 55
          666 77""";
    
      init(15, text);

      int foldStart = text.indexOf("5");
      int foldEnd = text.indexOf("7");
      myFixture.addCollapsedFoldRegion(foldStart, foldEnd, "...");

      int selectionStart = text.indexOf("2");
      int selectionEnd = text.indexOf("4");
      myFixture.getEditor().getSelectionModel().setSelection(selectionStart, selectionEnd);
    
      myFixture.delete();
      assertEquals(new VisualPosition(1, 8),
                   myFixture.getEditor().offsetToVisualPosition(myFixture.getEditor().getDocument().getTextLength() - 1));
    });
  }

  @Test
  public void testWrapAtFirstNonWsSymbolWithCustomIndent() {
    myFixture.runTest(() -> {
      String text = 
        "   1111111111111111111111111111111";
      init(10, text);
      myFixture.getEditor().getSettings().setCustomSoftWrapIndent(0);
      myFixture.getEditor().getSettings().setUseCustomSoftWrapIndent(true);
      //Trigger soft wraps recalculation.
      ((SoftWrapModelImpl)myFixture.getEditor().getSoftWrapModel()).prepareToMapping();

      myFixture.verifySoftWrapPositions(3, 11, 19, 27);
    });
  }

  @Test
  public void testLeadingTabWithShiftedWidth() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-76353. The point is that we need to consider cached information about tab symbols width during logical
      // position to offset mapping
      String text = "\t test";
      init(15, text);
      ((EditorImpl)myFixture.getEditor()).setPrefixTextAndAttributes(" ", new TextAttributes());
      myFixture.getEditor().getCaretModel().moveToOffset(text.length());
    });
  }

  @Test
  public void testSoftWrapCacheReset() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-76537 - the point is to drop cached document info on complete soft wraps recalculation
      String text =
        """
          \t first line
          \t second line
          \t third line""";
    
      // Make soft wraps to build a document info cache.
      init(40, text);
    
      // Modify document while soft wraps processing is off.
      final EditorSettings settings = myFixture.getEditor().getSettings();
      settings.setUseSoftWraps(false);
      int startOffset = text.indexOf("\t third") - 1;
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                               () -> myFixture.getEditor().getDocument().deleteString(startOffset, text.length()));

      // Enable soft wraps and ensure that the cache is correctly re-built.
      settings.setUseSoftWraps(true);
    
      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());
      myFixture.type("\n test");
    
      final int offset = myFixture.getEditor().getDocument().getTextLength() - 1;
      final LogicalPosition logicalPosition = myFixture.getEditor().offsetToLogicalPosition(offset);
      assertEquals(offset, myFixture.getEditor().logicalPositionToOffset(logicalPosition));

      final VisualPosition visualPosition = myFixture.getEditor().offsetToVisualPosition(offset);
      assertEquals(visualPosition, myFixture.getEditor().logicalToVisualPosition(logicalPosition));
      assertEquals(logicalPosition, myFixture.getEditor().visualToLogicalPosition(visualPosition));
    });
  }

  @Test
  public void testSoftWrapsRecalculationOnTabWidthChange() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-78616 - the point is to recalculate soft wraps when tab width is changed.
      String text = 
        "\t<caret> my text";

      // Build soft wraps cache.
      init(40, text);
    
      VisualPosition caretPositionBefore = myFixture.getEditor().getCaretModel().getVisualPosition();

      // Change tab size.
      final CommonCodeStyleSettings.IndentOptions indentOptions = getCurrentCodeStyleSettings().getIndentOptions(PlainTextFileType.INSTANCE);

      assertNotNull(indentOptions);
      indentOptions.TAB_SIZE++;

      ((EditorImpl)myFixture.getEditor()).reinitSettings();
      assertEquals(
        new VisualPosition(caretPositionBefore.line, caretPositionBefore.column + 1),
        myFixture.getEditor().getCaretModel().getVisualPosition()
      );
    });
  }

  @Test
  public void testNoPreliminarySoftWrapAtLineEnd() {
    myFixture.runTest(() -> {
      // We used to make soft wrap when the string was couple of visual columns before the right screen edge even if it could
      // be completely shown.
      init(52, "a b c", 10);
      assertThat(getSoftWrapModel().getRegisteredSoftWraps()).isEmpty();
    });
  }

  @Test
  public void testNoPreliminarySoftWrapBeforeFoldingAtLineEnd() {
    myFixture.runTest(() -> {
      final String text = "a b c test";
      init(71, text, 10);
      myFixture.addCollapsedFoldRegion(text.indexOf("t"), text.length(), ".");
      assertThat(getSoftWrapModel().getRegisteredSoftWraps()).isEmpty();
    });
  }

  @Test
  public void testMultiLineFoldRegionBeforeWrapPosition() {
    myFixture.runTest(() -> {
      final String text =
        """
          package org.denis;

          public class BrokenAlignment {

              void method1(int a) {
              }

              Object method2(Object ... data) {
                  return new Runnable() {
                      public void run() {
                          System.out.println();
                      }
                  };
              }

          }""";
      init(511, text, JavaFileType.INSTANCE, 10);
    
      myFixture.addCollapsedFoldRegion(text.indexOf("new Runnable"), text.indexOf("System"), "Runnable() { ");
    
      int start = text.indexOf("System");
      start = text.indexOf("\n", start);
      int end = text.indexOf(';', start);
      myFixture.addCollapsedFoldRegion(start, end, " }");
    
      final List<? extends SoftWrap> wraps = getSoftWrapModel().getRegisteredSoftWraps();
      assertEquals(1, wraps.size());

      assertEquals(start, wraps.getFirst().getStart());
      assertEquals(myFixture.getEditor().offsetToVisualPosition(start), myFixture.getEditor().offsetToVisualPosition(start + 1));
      assertEquals(myFixture.getEditor().offsetToVisualPosition(start).line, myFixture.getEditor().offsetToVisualPosition(end - 1).line);
    });
  }
  
  @Test
  public void testEnsureBeforeSoftWrapSignIsVisible() {
    myFixture.runTest(() -> {
      final String text = "a.b.c.d";
      init(61, text, 10);
    
      checkSoftWraps(text.indexOf('c') + 1);
    });
  }

  @Test
  public void testWrapAfterCollapsedFoldRegion() {
    myFixture.runTest(() -> {
      final String text =
        "this is a long text to fold more";
      init(12, text);
    
      int start = text.indexOf("long");
      int end = text.indexOf("more");
      myFixture.addCollapsedFoldRegion(start, end, "placeholder which is long enough to be wrapped");
    
      checkSoftWraps(start, end);
    });
  }

  @Test
  public void testFoldRegionWithLongPlaceholderText() {
    myFixture.runTest(() -> {
      final String text =
        "this is a string with(fold region), end)\n" +
        "second string";
      init(40, text);

      int start = text.indexOf("fold");
      int end = text.indexOf(',');
      myFixture.addCollapsedFoldRegion(start, end, "this is a really long fold region placeholder");
    
      checkSoftWraps(start, end);
      assertEquals(new VisualPosition(3, 0), myFixture.getEditor().offsetToVisualPosition(text.indexOf("second")));
    });
  }

  @Test
  public void testDeleteWhenCaretBeforeSoftWrap() {
    myFixture.runTest(() -> {
      final String text =
        "text 1234";
      init(7, text);

      final int offset = text.indexOf("123");
      checkSoftWraps(offset);

      final CaretModel caretModel = myFixture.getEditor().getCaretModel();
      caretModel.moveToOffset(offset);
      caretModel.moveCaretRelatively(-1, 0, false, false, false);
      assertEquals(offset, caretModel.getOffset()); // Navigating from 'after soft wrap' to the 'before soft wrap' position.
      myFixture.delete();
      assertEquals("text 234", myFixture.getEditor().getDocument().getText());
    });
  }

  @Test
  public void testSelectionOfLineWithSoftWrapAndFoldRegion() {
    myFixture.runTest(() -> {
      final String text =
        """
          123
          fold line 1
          fold line 2 456""";
      init(6, text);
      myFixture.addCollapsedFoldRegion(2, text.indexOf("4"), "...");
      myFixture.getEditor().getSelectionModel().selectLineAtCaret();
      assertEquals(text, myFixture.getEditor().getSelectionModel().getSelectedText());
    });
  }
  
  @Test
  public void testCaretPositionAfterLineRemoval() {
    myFixture.runTest(() -> {
      final String text =
        """
          123456
          123456
          line towrapbecauseitislong
          923456""";
      init(8, text);
      final CaretModel caretModel = myFixture.getEditor().getCaretModel();
      caretModel.moveToOffset(text.indexOf("e"));
      final VisualPosition position = caretModel.getVisualPosition();
      myFixture.deleteLine();
      assertEquals(text.substring(0, text.indexOf("line")) + text.substring(text.indexOf('9')),
                   myFixture.getEditor().getDocument().getText());
      assertEquals(position, caretModel.getVisualPosition());
    });
  }

  @Test
  public void testNoUnnecessaryHorizontalScrollBar() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-87184
      final String text = "12345678 abcdefgh";
      init(15, 10, text);
      myFixture.getEditor().getCaretModel().moveToOffset(text.length());
      myFixture.backspace();
      myFixture.verifySoftWrapPositions(9);
    });
  }

  @Test
  public void testCaretInsideFoldRegionOnCollapse() {
    myFixture.runTest(() -> {
      // IDEA-89874
      String text = "one two three";
      init(30, text);
      int foldStart = text.indexOf("two");
      myFixture.addFoldRegion(foldStart, foldStart + "two".length(), "...");
      myFixture.getEditor().getCaretModel().moveToOffset(foldStart + 1);
      FoldRegion foldRegion = myFixture.getFoldRegion(foldStart);
      assertNotNull(foldRegion);
      assertTrue(foldRegion.isExpanded());
    
      myFixture.toggleFoldRegionState(foldRegion, false);

      assertFalse(foldRegion.isExpanded());
      assertEquals(foldStart, myFixture.getEditor().getCaretModel().getOffset());
    });
  }

  @Test
  public void testFoldRegionEndingAtLineStart() {
    myFixture.runTest(() -> {
      init(100, "aaa\nbbb\nccc\nddd");
      myFixture.addCollapsedFoldRegion(4, 8, "...");
      myFixture.addCollapsedFoldRegion(13, 15, "...");

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> myFixture.getEditor().getDocument().insertString(10, "C"));

      // verify that cached layout data is intact after document change and position recalculation is done correctly
      assertEquals(new LogicalPosition(0, 0), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(0, 0)));
    });
  }
  
  @Test
  public void testOnlyMinimalRangeIsRecalculatedOnDocumentChange() {
    myFixture.runTest(() -> {
      myFixture.init("aa bb cc dd ee ff<caret> gg hh ii jj", PlainTextFileType.INSTANCE);
      EditorTestUtil.configureSoftWraps(myFixture.getEditor(), 8);
      myFixture.verifySoftWrapPositions(8, 15, 21);
    
      final IncrementalCacheUpdateEvent[] event = new IncrementalCacheUpdateEvent[1];
      final var eventCount = new AtomicInteger(0);
      ((SoftWrapModelImpl)myFixture.getEditor().getSoftWrapModel()).addSoftWrapParsingListener(new SoftWrapParsingListener() {
        @Override
        public void onRegionReparseEnd(@NotNull IncrementalCacheUpdateEvent e) {
          eventCount.incrementAndGet();
          event[0] = e;
        }
      });
    
      myFixture.type(' ');
    
      myFixture.verifySoftWrapPositions(8, 15, 22);
      assertNotNull(event[0]);
      assertEquals(8, event[0].getStartOffset());
      assertEquals(22, event[0].getActualEndOffset());
      assertEquals(1, eventCount.get());
    });
  }
  
  @Test
  public void testPositionsAreCorrectAfterIncrementalRecalculation() {
    myFixture.runTest(() -> {
      myFixture.initText("abra<caret> cadabra");
      myFixture.configureSoftWraps(10);
      myFixture.type(' ');
    
      assertEquals(new LogicalPosition(0, 6), myFixture.getEditor().offsetToLogicalPosition(6));
      assertEquals(new VisualPosition(1, 1), myFixture.getEditor().offsetToVisualPosition(6));
    });
  }

  @Test
  public void testSoftWrappingWithFoldRegionAndTabs() {
    myFixture.runTest(() -> {
      myFixture.initText("foldA\t\t\t\t");
      myFixture.addCollapsedFoldRegion(0, 4, ".");
      myFixture.configureSoftWraps(10);

      assertNull(myFixture.getEditor().getSoftWrapModel().getSoftWrap(4));
    });
  }
  
  @Test
  public void testSoftWrapsAreNotCreatedInsideFoldRegions() {
    myFixture.runTest(() -> {
      myFixture.initText("\t\t\taaaaa\t");
      myFixture.addCollapsedFoldRegion(7, 9, ".");
      myFixture.configureSoftWraps(10);

      assertNull(myFixture.getEditor().getSoftWrapModel().getSoftWrap(8));
    });
  }
  
  @Test
  public void testUnbreakableLinesDontAffectFollowingLines() {
    myFixture.runTest(() -> {
      myFixture.initText("unbreakableLine\nshort line");
      myFixture.configureSoftWraps(10);
    
      myFixture.verifySoftWrapPositions(10);
    });
  }
  
  @Test
  public void testFoldRegionPreventsLaterWrapping() {
    myFixture.runTest(() -> {
      myFixture.initText("unbreakableText.txt");
      myFixture.configureSoftWraps(10);
      myFixture.verifySoftWrapPositions(10);
    
      myFixture.addCollapsedFoldRegion(12, 13, ".");

      myFixture.verifySoftWrapPositions(10);
      assertEquals(1, myFixture.getEditor().offsetToVisualPosition(19).line);
    });
  }
  
  @Test
  public void testMoveWithFoldRegionInside() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef\nghi\n");
      myFixture.configureSoftWraps(100);
      myFixture.addCollapsedFoldRegion(0, 4, "...");

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                               () -> ((DocumentEx)myFixture.getEditor().getDocument()).moveText(0, 4, 12));

      assertEquals(new LogicalPosition(2, 0), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(2, 1)));
    });
  }

  private static final int DEFAULT_SYMBOL_WIDTH_PX = 10;
  protected void init(final int visibleWidthInColumns, @NotNull String fileText) {
    init(visibleWidthInColumns, 10, fileText);
  }

  protected void init(final int visibleWidthInColumns, @NotNull String fileText, boolean useCustomSoftWrapIndent) {
    init(visibleWidthInColumns * DEFAULT_SYMBOL_WIDTH_PX, fileText, PlainTextFileType.INSTANCE, DEFAULT_SYMBOL_WIDTH_PX, useCustomSoftWrapIndent);
  }

  protected void init(final int visibleWidthInColumns, final int symbolWidthInPixels, @NotNull String fileText) {
    init(visibleWidthInColumns * symbolWidthInPixels, fileText, symbolWidthInPixels);
  }

  protected void init(final int visibleWidth, @NotNull String fileText, int symbolWidth) {
    init(visibleWidth, fileText, PlainTextFileType.INSTANCE, symbolWidth);
  }

  protected void init(final int visibleWidth, @NotNull String fileText, @NotNull FileType fileType, final int symbolWidth) {
    init(visibleWidth, fileText, fileType, symbolWidth, true);
  }

  protected void init(final int visibleWidth, @NotNull String fileText, @NotNull FileType fileType, final int symbolWidth, boolean useCustomSoftWrapIndent) {
    myFixture.init(fileText, fileType);
    EditorTestUtil.configureSoftWraps(myFixture.getEditor(), visibleWidth, 1000, symbolWidth, useCustomSoftWrapIndent);
  }

  protected void checkSoftWraps(int... startOffsets) {
    assertArrayEquals(startOffsets, getSoftWrapModel().getRegisteredSoftWraps().stream().mapToInt(s -> s.getStart()).toArray());
  }

  protected SoftWrapModelImpl getSoftWrapModel() {
    return (SoftWrapModelImpl)myFixture.getEditor().getSoftWrapModel();
  }
}
