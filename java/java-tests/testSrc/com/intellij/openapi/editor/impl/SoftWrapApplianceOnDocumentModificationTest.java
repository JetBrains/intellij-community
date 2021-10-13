// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapAwareDocumentParsingListenerAdapter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Denis Zhdanov
 */
public class SoftWrapApplianceOnDocumentModificationTest extends AbstractEditorTest {

  private boolean mySmartHome;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getEditor() == null) {
      return;
    }

    EditorSettings settings = getEditor().getSettings();
    mySmartHome = settings.isSmartHome();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (getEditor() != null) {
        EditorSettings settings = getEditor().getSettings();
        settings.setUseSoftWraps(false);
        settings.setSmartHome(mySmartHome);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSoftWrapAdditionOnTyping() {
    String text =
      "this is a test string that is expected to end just before right margin<caret>";
    init(100, text);

    int offset = getEditor().getDocument().getTextLength() + 1;
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());
    type(" thisisalongtokenthatisnotexpectedtobebrokenintopartsduringsoftwrapping");
    SoftWrap softWrap = getEditor().getSoftWrapModel().getSoftWrap(offset);
    
    // This test fails randomly at TeamCity. The logging below is intended to help analyzing the problem.
    if (softWrap == null) {
      fail(String.format(
        "Expected soft wrap to be located on offset %d. Actual: %s. Document text: %s",
        offset, getSoftWrapModel().getRegisteredSoftWraps(), getEditor().getDocument().getCharsSequence()
      ));
    }
  }

  public void testLongLineOfIdSymbolsIsSoftWrapped() {
    String text =
      "abcdefghijklmnopqrstuvwxyz<caret>\n" +
      "123\n" +
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    init(15, text);
    verifySoftWrapPositions(14, 45);
    type('1');
    verifySoftWrapPositions(14, 46);
    type(" test");
    verifySoftWrapPositions(14, 27, 51);
  }

  public void testFoldRegionCollapsing() {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}";

    init(40, text);
    final FoldingModel foldingModel = getEditor().getFoldingModel();
    assertEmpty(foldingModel.getAllFoldRegions());

    final int startOffset = text.indexOf('{');
    final int endOffset = text.indexOf('}') + 1;

    VisualPosition foldStartPosition = getEditor().offsetToVisualPosition(startOffset);

    addFoldRegion(startOffset, endOffset, "...");

    final FoldRegion foldRegion = getFoldRegion(startOffset);
    assertNotNull(foldRegion);
    assertTrue(foldRegion.isExpanded());
    toggleFoldRegionState(foldRegion, false);

    // Expecting that all offsets that belong to collapsed fold region point to the region's start.
    assertEquals(foldStartPosition, getEditor().offsetToVisualPosition(startOffset + 5));
  }

  public void testTypingEnterAtDocumentEnd() {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}<caret>";

    init(40, text);
    type('\n');
    VisualPosition position = getEditor().getCaretModel().getVisualPosition();
    assertEquals(new VisualPosition(5, 0), position);
  }

  public void testDeleteDocumentTail() {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}\n" +
      "abcde";

    init(40, text);
    int offset = text.indexOf("abcde");
    getEditor().getSelectionModel().setSelection(offset, text.length());
    delete();
    assertEquals(new VisualPosition(5, 0), getEditor().getCaretModel().getVisualPosition());
  }

  public void testTypingTabOnLastEmptyLine() {
    String text =
      "class Test {\n" +
      "}\n" +
      "<caret>";

    init(40, text);
    type('\t');
    assertEquals(new VisualPosition(2, 4), getEditor().getCaretModel().getVisualPosition());
  }

  public void testTrailingFoldRegionRemoval() {
    String text =
      "public class BrokenAlignment {\n" +
      "    @SuppressWarnings({ \"SomeInspectionIWantToIgnore\" })\n" +
      "    public void doSomething(int x, int y) {\n" +
      "    }\n" +
      "}";

    init(100, text);

    int startFoldOffset = text.indexOf('@');
    int endFoldOffset = text.indexOf(')');
    addCollapsedFoldRegion(startFoldOffset, endFoldOffset, "/SomeInspectionIWantToIgnore/");

    int endSelectionOffset = text.lastIndexOf("}\n") + 1;
    getEditor().getSelectionModel().setSelection(startFoldOffset, endSelectionOffset);

    delete();
    // Don't expect any exceptions here.
  }

  public void testTypeNewLastLineAndSymbolOnIt() {
    // Inspired by IDEA-59439
    String text = 
      "This is a test document\n" +
      "line1\n" +
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "line5\n" +
      "line6<caret>";
    
    init(100, text);
    type("\nq");
    assertEquals(new VisualPosition(7, 1), getEditor().offsetToVisualPosition(getEditor().getDocument().getTextLength()));
  }
  
  public void testTrailingSoftWrapOffsetShiftOnTyping() {
    // The main idea is to type on a logical line before soft wrap in order to ensure that its offset is correctly shifted back.
    String text = 
      "line1<caret>\n" +
      "second line that is long enough to be soft wrapped";
    init(15, text);

    TIntHashSet offsetsBefore = collectSoftWrapStartOffsets(1);
    assertTrue(!offsetsBefore.isEmpty());
    
    type('2');
    final TIntHashSet offsetsAfter = collectSoftWrapStartOffsets(1);
    assertSame(offsetsBefore.size(), offsetsAfter.size());
    offsetsBefore.forEach(value -> {
      assertTrue(offsetsAfter.contains(value + 1));
      return true;
    });
  }
  
  public void testSoftWrapAwareMappingAfterLeadingFoldRegionCollapsing() {
    String text =
      "line to fold 1\n" +
      "line to fold 2\n" +
      "line to fold 3\n" +
      "ordinary line 1\n" +
      "ordinary line 2\n" +
      "ordinary line 3\n" +
      "ordinary line 4\n" +
      "line that is long enough to be soft wrapped\n" +
      "ordinary line 5\n" +
      "ordinary line 6\n" +
      "ordinary line 7\n" +
      "ordinary line 8\n";
    
    init(30, text);
    LogicalPosition position = getEditor().visualToLogicalPosition(new VisualPosition(8, 0));
    assertSame(7, position.line); // Position from soft-wrapped part of the line
    
    addCollapsedFoldRegion(0, text.indexOf("ordinary line 1") - 1, "...");
    assertSame(7, getEditor().visualToLogicalPosition(new VisualPosition(6, 0)).line); // Check that soft wraps cache is correctly updated
  }
  
  public void testCaretPositionOnFoldRegionExpand() {
    // We had a problem that caret preserved its visual position instead of offset. This test checks that.
    
    String text = 
      "/**\n" +
      " * This is a test comment\n" +
      " */\n" +
      "public class Test {\n" +
      "}";
    init(100, text);

    addCollapsedFoldRegion(0, text.indexOf("public") - 1, "/**...*/");
    
    int offset = text.indexOf("class");
    CaretModel caretModel = getEditor().getCaretModel();
    caretModel.moveToOffset(offset);
    assertEquals(offset, caretModel.getOffset());
    assertEquals(1, caretModel.getVisualPosition().line);
    
    toggleFoldRegionState(getFoldRegion(0), true);
    assertEquals(3, caretModel.getVisualPosition().line);
    assertEquals(offset, caretModel.getOffset());
  }
  
  public void testBackspaceAtTheEndOfSoftWrappedLine() {
    // There was a problem that removing text from the last document line that was soft-wrapped removed soft wraps as well.
    String text = 
      "This a long string that is expected to be wrapped in more than one visual line<caret>";
    init(20, text);

    List<SoftWrap> softWrapsBeforeModification = new ArrayList<>(getSoftWrapModel().getRegisteredSoftWraps());
    assertTrue(softWrapsBeforeModification.size() > 0);
    
    backspace();
    assertEquals(softWrapsBeforeModification, getSoftWrapModel().getRegisteredSoftWraps());
  }
  
  public void testRemoveOfAllSymbolsFromLastLine() {
    // There was a problem that removing all text from the last document line corrupted soft wraps cache.
    String text =
      "Line1\n" +
      "Long line2 that is expected to be soft-wrapped<caret>";
    init(20, text);

    List<SoftWrap> softWrapsBeforeModification = new ArrayList<>(getSoftWrapModel().getRegisteredSoftWraps());
    assertTrue(softWrapsBeforeModification.size() > 0);

    int offset = getEditor().getCaretModel().getOffset();
    VisualPosition positionBeforeModification = getEditor().offsetToVisualPosition(offset);
    type("\n123");
    getEditor().getSelectionModel().setSelection(offset + 1, getEditor().getDocument().getTextLength());
    delete();
    assertEquals(softWrapsBeforeModification, getSoftWrapModel().getRegisteredSoftWraps());
    assertEquals(positionBeforeModification, getEditor().offsetToVisualPosition(offset));
  }
  
  public void testTypingBeforeCollapsedFoldRegion() {
    // We had a problem that soft wraps cache entries that lay after the changed region were considered to be affected by the change.
    // This test checks that situation.
    String text =
      "\n" +
      "fold line1\n" +
      "fold line2\n" +
      "fold line3\n" +
      "fold line4\n" +
      "normal line1";
    init(20, text);
    
    int afterFoldOffset = text.indexOf("normal line1");
    addCollapsedFoldRegion(text.indexOf("fold line1") + 2, afterFoldOffset - 1, "...");
    VisualPosition beforeModification = getEditor().offsetToVisualPosition(afterFoldOffset);
    
    getEditor().getCaretModel().moveToOffset(0);
    type('a');
    
    assertEquals(beforeModification, getEditor().offsetToVisualPosition(afterFoldOffset + 1));
  }
  
  public void testCollapsedFoldRegionPlaceholderThatExceedsVisibleWidth() {
    String text =
      "line1\n" +
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "line5\n" +
      "this is long line that is expected to be soft-wrapped\n" +
      "line6";
    init(15, text);

    assertTrue(!getSoftWrapModel().getRegisteredSoftWraps().isEmpty());

    int offsetAfterSingleLineFoldRegion = text.indexOf("line2") - 1;
    int lineAfterSingleLineFoldRegion = getEditor().offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line;

    int offsetAfterMultiLineFoldRegion = text.indexOf("this") - 1;
    int lineAfterMultiLineFoldRegion = getEditor().offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line;

    int offsetAfterSoftWrap = text.indexOf("line6") - 1;
    VisualPosition beforePositionAfterSoftWrap = getEditor().offsetToVisualPosition(offsetAfterSoftWrap);
    
    // Add single-line fold region which placeholder text is long enough to exceed visual area width.
    addCollapsedFoldRegion(2, offsetAfterSingleLineFoldRegion, "this is a very long placeholder for the single-line fold region");
    assertEquals(lineAfterSingleLineFoldRegion + 1, getEditor().offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line);
    lineAfterSingleLineFoldRegion++;
    
    assertEquals(lineAfterMultiLineFoldRegion + 1, getEditor().offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line);
    lineAfterMultiLineFoldRegion++;
    
    beforePositionAfterSoftWrap = new VisualPosition(beforePositionAfterSoftWrap.line + 1, beforePositionAfterSoftWrap.column); 
    assertEquals(beforePositionAfterSoftWrap, getEditor().offsetToVisualPosition(offsetAfterSoftWrap));
    
    // Add multi-line fold region which placeholder is also long enough to exceed visual area width.
    addCollapsedFoldRegion(text.indexOf("line2") + 2, offsetAfterMultiLineFoldRegion, "long enough placeholder for multi-line fold region");
    assertEquals(lineAfterSingleLineFoldRegion, getEditor().offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line);
    assertEquals(lineAfterMultiLineFoldRegion - 2, getEditor().offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line);
    beforePositionAfterSoftWrap = new VisualPosition(beforePositionAfterSoftWrap.line - 2, beforePositionAfterSoftWrap.column);
    assertEquals(beforePositionAfterSoftWrap, getEditor().offsetToVisualPosition(offsetAfterSoftWrap));
  }
  
  public void testInsertNewStringAndTypeOnItBeforeFoldRegion() {
    // There was incorrect processing of fold regions when document change was performed right before them.
    String text =
      "/**\n" +
      " * comment\n" +
      " */\n" +
      "class Test {\n" +
      "}";
    init(100, text);
    String placeholder = "/**...*/";
    addCollapsedFoldRegion(0, text.indexOf("class") - 1, placeholder);
    
    getEditor().getCaretModel().moveToOffset(0);
    type("\n");
    getEditor().getCaretModel().moveToOffset(0);
    type("import");
    
    // Check that fold region info is still correct.
    int foldStartOffset = getEditor().getDocument().getText().indexOf("/**");
    int foldEndOffset = getEditor().getDocument().getText().indexOf("class") - 1;
    assertEquals(new VisualPosition(1, 0), getEditor().offsetToVisualPosition(foldStartOffset));
    assertEquals(new VisualPosition(1, 0), getEditor().offsetToVisualPosition((foldStartOffset + foldEndOffset) / 2));
    assertEquals(new VisualPosition(1, placeholder.length()), getEditor().offsetToVisualPosition(foldEndOffset));
  }
  
  public void testUpdateFoldRegionDataOnTextRemoveBeforeIt() {
    String text =
      "1\n" +
      "/**\n" +
      " * comment\n" +
      " */\n" +
      "class Test {\n" +
      "}";
    init(100, text);
    String placeholder = "/**...*/";
    addCollapsedFoldRegion(2, text.indexOf("class") - 1, placeholder);
    
    getEditor().getCaretModel().moveToOffset(0);
    delete();
    assertEquals(new VisualPosition(1, placeholder.length()), getEditor().offsetToVisualPosition(text.indexOf("class") - 2));
  }
  
  public void testRemoveCollapsedFoldRegionThatStartsLogicalLine() {
    // There was a problem that soft wraps cache updated on document modification didn't contain information about removed
    // fold region but fold model still provided cached information about it.
    String text =
      "package org;\n" +
      "\n" +
      "@SuppressWarnings(\"all\")\n" +
      "class Test {\n" +
      "}";
    
    init(100, text);
    int startOffset = text.indexOf("@");
    int endOffset = text.indexOf("class") - 1;
    addCollapsedFoldRegion(startOffset, endOffset, "xxx");
    
    // Delete collapsed fold region that starts logical line.
    getEditor().getSelectionModel().setSelection(startOffset, endOffset);
    delete();
    
    assertEquals(startOffset, getEditor().logicalPositionToOffset(getEditor().visualToLogicalPosition(new VisualPosition(2, 0))));
  }
  
  public void testFoldRegionThatStartsAtLineEnd() {
    String text =
      "line1\n" +
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "line5";
    
    init(63, text, 10);
    int start = text.indexOf("line3") - 1;
    addCollapsedFoldRegion(start, text.length(), "...");
    assertEquals(1, getSoftWrapModel().getRegisteredSoftWraps().size());
    assertEquals(start, getSoftWrapModel().getRegisteredSoftWraps().get(0).getStart());
  }
  
  public void testHomeProcessing() {
    String text = 
      "class Test {\n" +
      "    public String s = \"this is a long string literal that is expected to be soft-wrapped into multiple visual lines\";\n" +
      "}";
    
    init(30, text, false);
    getEditor().getCaretModel().moveToOffset(text.indexOf("}") - 1);

    checkSoftWraps(41, 66, 89, 116);

    CaretModel caretModel = getEditor().getCaretModel();
    home();
    assertEquals(116, caretModel.getOffset());
    assertEquals(new VisualPosition(5, 1), caretModel.getVisualPosition());
    home();
    assertEquals(116, caretModel.getOffset());
    assertEquals(new VisualPosition(5, 0), caretModel.getVisualPosition());
    
    // Expecting caret to be located on the first non-white space symbol of non-soft wrapped line.
    home();
    assertEquals(text.indexOf("public"), caretModel.getOffset());
    assertEquals(new VisualPosition(1, 4), caretModel.getVisualPosition());
  }

  public void testCustomWrap() {
    String text =
      "class Test {\n" +
      "    public String s = \"this is a long string literal that is expected to be soft-wrapped into multiple visual lines\";\n" +
      "}";

    init(30, text, true);
    checkSoftWraps(41, 65, 89, 107);
  }

  public void testEndProcessing() {
    String text =
      "class Test {\n" +
      "    public String s = \"this is a long string literal that is expected to be soft-wrapped into multiple visual lines\";   \n" +
      "}";

    init(30, text, false);
    getEditor().getCaretModel().moveToOffset(text.indexOf("\n") + 1);

    checkSoftWraps(41, 66, 89, 116);

    CaretModel caretModel = getEditor().getCaretModel();
    end();
    assertEquals(40, caretModel.getOffset());
    assertEquals(new VisualPosition(1, 27), caretModel.getVisualPosition());
    end();
    assertEquals(41, caretModel.getOffset());
    assertEquals(new VisualPosition(1, 28), caretModel.getVisualPosition());
    
    // Check that caret is placed on a last non-white space symbol on current logical line.
    end();
    int lastNonWhiteSpaceSymbolOffset = text.indexOf("\";") + 2;
    assertEquals(lastNonWhiteSpaceSymbolOffset, caretModel.getOffset());
    assertEquals(getEditor().offsetToVisualPosition(lastNonWhiteSpaceSymbolOffset), caretModel.getVisualPosition());
    
    // Check that caret is place to the very end of the logical line.
    end();
    int lastSymbolOffset = getEditor().getDocument().getLineEndOffset(caretModel.getLogicalPosition().line);
    assertEquals(lastSymbolOffset, caretModel.getOffset());
    assertEquals(getEditor().offsetToVisualPosition(lastSymbolOffset), caretModel.getVisualPosition());
  }
  
  public void testSoftWrapToHardWrapConversion() {
    String text =
      "this is line 1\n" +
      "this is line 2\n" +
      "this is line 3\n" +
      "this is line 4\n" +
      "this is line 5";
    
    init(71, text, 10);
    VisualPosition changePosition = new VisualPosition(1, 0);
    getEditor().getCaretModel().moveToVisualPosition(changePosition);
    
    int logicalLinesBefore = getEditor().offsetToLogicalPosition(text.length()).line;
    int offsetBefore = getEditor().getCaretModel().getOffset();
    
    LogicalPosition logicalPositionBefore = getEditor().visualToLogicalPosition(changePosition);
    assertEquals(1, EditorUtil.getSoftWrapCountAfterLineStart(getEditor(), logicalPositionBefore));
    assertTrue(logicalPositionBefore.column > 0);
    
    SoftWrap softWrap = getSoftWrapModel().getSoftWrap(offsetBefore);
    assertNotNull(softWrap);
    
    type('a');

    LogicalPosition logicalPositionAfter = getEditor().visualToLogicalPosition(changePosition);
    assertEquals(new LogicalPosition(1, 0), logicalPositionAfter);
    assertEquals(offsetBefore + softWrap.getText().length() + 1, getEditor().getCaretModel().getOffset());
    assertEquals(logicalLinesBefore + 1, getEditor().offsetToLogicalPosition(text.length()).line);
  }
  
  public void testPastingInsideSelection() {
    String text = 
      "this is line number 0\n" +
      "this is line number 1\n" +
      "this is line number 2\n" +
      "this is line number 3\n" +
      "this is line number 4\n" +
      "this is line number 5\n" +
      "this is line number 6\n" +
      "this is the last line";
    
    init(100, text);
    int lineToSelect = 4;
    getEditor().getCaretModel().moveToOffset(text.indexOf("number " + lineToSelect));
    Document document = getEditor().getDocument();
  
    int startOffset = document.getLineStartOffset(lineToSelect);
    int endOffset = document.getLineEndOffset(lineToSelect);
    getEditor().getSelectionModel().setSelection(startOffset, endOffset);
    
    VisualPosition positionBefore = getEditor().offsetToVisualPosition(document.getLineStartOffset(lineToSelect + 1));
    List<SoftWrap> softWrapsBefore = new ArrayList<>(getSoftWrapModel().getRegisteredSoftWraps());
    
    copy();
    paste();
    
    assertEquals(positionBefore, getEditor().offsetToVisualPosition(document.getLineStartOffset(lineToSelect + 1)));
    assertEquals(softWrapsBefore, getSoftWrapModel().getRegisteredSoftWraps());
  }
  
  public void testRemoveHugeLogicalLineThatLaysBeforeSoftWrappedLines() {
    String text =
      "short line\n" +
      "this is a long line that is expected to be soft wrapped into more than one or even two visual lines\n" +
      "1. just a line that is long enough to be soft wrapped\n" +
      "2. just a line that is long enough to be soft wrapped\n" +
      "3. just a line that is long enough to be soft wrapped\n" +
      "4. just a line that is long enough to be soft wrapped";
    
    init(15, text);
    Document document = getEditor().getDocument();
    int start = document.getLineStartOffset(1);
    int end = document.getLineEndOffset(1) + 1;
    int visualLinesToRemove = getSoftWrapModel().getSoftWrapsForLine(1).size() + 1;
    
    List<VisualPosition> positionsBefore = new ArrayList<>();
    for (int i = end; i < text.length(); i++) {
      positionsBefore.add(getEditor().offsetToVisualPosition(i));
    }
    Collections.reverse(positionsBefore);

    getEditor().getSelectionModel().setSelection(start, end);
    delete();
    
    // Check that all remembered positions are just shifted to expected number of visual lines.
    for (int i = start; i < document.getTextLength(); i++) {
      VisualPosition position = positionsBefore.remove(positionsBefore.size() - 1);
      assertEquals(new VisualPosition(position.line - visualLinesToRemove, position.column), getEditor().offsetToVisualPosition(i));
    }
  }
  
  public void testVerticalCaretShiftOnLineComment() {
    String text =
      "1. just a line that is long enough to be soft wrapped\n" +
      "2. just a line that is long enough to be soft wrapped\n" +
      "3. just a line that is long enough to be soft wrapped\n" +
      "4. just a line that is long enough to be soft wrapped";
    init(15, text);

    CaretModel caretModel = getEditor().getCaretModel();
    caretModel.moveToOffset(text.indexOf("2.") + 2);
    new CommentByLineCommentAction().actionPerformedImpl(getProject(), getEditor());
    
    assertEquals(getEditor().offsetToLogicalPosition(text.indexOf("3.") + 2), caretModel.getLogicalPosition());
  }
  
  private TIntHashSet collectSoftWrapStartOffsets(int documentLine) {
    TIntHashSet result = new TIntHashSet();
    for (SoftWrap softWrap : getEditor().getSoftWrapModel().getSoftWrapsForLine(documentLine)) {
      result.add(softWrap.getStart());
    }
    return result;
  }
  
  public void testNonSmartHome() {
    String text =
      "     this is a string that starts with white space and is long enough to be soft-wrapped\n" +
      " this is a 'prefix' text before collapsed multi-line folding that is long enough to be soft-wrapped first fold line\n" +
      "second fold line";
    init(30, text);
    
    addCollapsedFoldRegion(text.indexOf("first fold line"), text.length(), "...");

    List<? extends SoftWrap> softWraps = getSoftWrapModel().getRegisteredSoftWraps();
    assertTrue(!softWraps.isEmpty());

    CaretModel caretModel = getEditor().getCaretModel();
    SoftWrap softWrap = softWraps.get(0);
    
    // Test non-smart home
    getEditor().getSettings().setSmartHome(false);
    caretModel.moveToOffset(softWrap.getStart() + 1);
    int visLine = caretModel.getVisualPosition().line;
    home();
    assertEquals(new VisualPosition(visLine, 0), caretModel.getVisualPosition());
    
    caretModel.moveToOffset(text.length());
    home();
    visLine = caretModel.getVisualPosition().line;
    assertEquals(new VisualPosition(visLine, 0), caretModel.getVisualPosition());
  }
  
  public void testFoldRegionsUpdate() {
    String text = 
      "import java.util.List;\n" +
      "import java.util.ArrayList;\n" +
      "\n" +
      "class Test {\n" +
      "}";
    init(40, text);
    
    final int foldStartOffset = "import".length() + 1;
    int foldEndOffset = text.indexOf("class") - 2;
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...");
    
    // Simulate addition of the new import that modifies existing fold region.
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getEditor()
      .getDocument().insertString(foldEndOffset, "\nimport java.util.Date;\n"));

    final FoldingModel foldingModel = getEditor().getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> {
      FoldRegion oldFoldRegion = getFoldRegion(foldStartOffset);
      assertNotNull(oldFoldRegion);
      foldingModel.removeFoldRegion(oldFoldRegion);
      
      int newFoldEndOffset = getEditor().getDocument().getText().indexOf("class") - 2;
      FoldRegion newFoldRegion = foldingModel.addFoldRegion(foldStartOffset, newFoldEndOffset, "...");
      assertNotNull(newFoldRegion);
      newFoldRegion.setExpanded(false);
    });
    CodeFoldingManager.getInstance(getProject()).updateFoldRegions(getEditor());
    assertEquals(new VisualPosition(2, 0), getEditor().logicalToVisualPosition(new LogicalPosition(5, 0)));
  }

  public void testModificationOfSoftWrappedFoldRegion() {
    String text =
      "import java.util.List;import java.util.ArrayList;import java.util.Collection;import java.util.Collections;\n" +
      "import java.util.LinkedList;\n" +
      "import java.util.Set;\n" +
      "\n" +
      "class Test {\n" +
      "}";
    init(40, text);

    final int foldStartOffset = text.indexOf("java.util.Collections");
    final int foldEndOffset = text.indexOf("class") - 2;
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...");

    int modificationOffset = text.indexOf("java.util.Set");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getEditor()
      .getDocument().insertString(modificationOffset, "import java.util.HashSet;\n"));

    // Used to get StackOverflowError here, hence, no additional checking is performed.
  }

  public void testLongSoftWrappedLineWithNonWrappedEndInTheMiddleOfDocument() {
    // Inspired by IDEA-70114
    
    String text = 
      "111\n" +
      "222\n" +
      "33333333 33333333333333333333333333333333\n" +
      "444";
    init(15, text);
    
    assertEquals(new LogicalPosition(3, 0), getEditor().visualToLogicalPosition(new VisualPosition(6, 0)));
  }
  
  public void testDeleteThatEndsOnLineWithMultiLineFoldRegion() {
    String text = 
      "111\n" +
      "222\n" +
      "333\n" +
      "444 55\n" +
      "666 77";
    
    init(15, text);

    int foldStart = text.indexOf("5");
    int foldEnd = text.indexOf("7");
    addCollapsedFoldRegion(foldStart, foldEnd, "...");

    int selectionStart = text.indexOf("2");
    int selectionEnd = text.indexOf("4");
    getEditor().getSelectionModel().setSelection(selectionStart, selectionEnd);
    
    delete();
    assertEquals(new VisualPosition(1, 8), getEditor().offsetToVisualPosition(getEditor().getDocument().getTextLength() - 1));
  }

  public void testWrapAtFirstNonWsSymbolWithCustomIndent() {
    String text = 
      "   1111111111111111111111111111111";
    init(10, text);
    getEditor().getSettings().setCustomSoftWrapIndent(0);
    getEditor().getSettings().setUseCustomSoftWrapIndent(true);
    //Trigger soft wraps recalculation.
    ((SoftWrapModelImpl)getEditor().getSoftWrapModel()).prepareToMapping();

    verifySoftWrapPositions(3, 11, 19, 27);
  }

  public void testLeadingTabWithShiftedWidth() {
    // Inspired by IDEA-76353. The point is that we need to consider cached information about tab symbols width during logical
    // position to offset mapping
    String text = "\t test";
    init(15, text);
    ((EditorImpl)getEditor()).setPrefixTextAndAttributes(" ", new TextAttributes());
    getEditor().getCaretModel().moveToOffset(text.length());
  }

  public void testSoftWrapCacheReset() {
    // Inspired by IDEA-76537 - the point is to drop cached document info on complete soft wraps recalculation
    String text =
      "\t first line\n" +
      "\t second line\n" +
      "\t third line";
    
    // Make soft wraps to build a document info cache.
    init(40, text);
    
    // Modify document while soft wraps processing is off.
    final EditorSettings settings = getEditor().getSettings();
    settings.setUseSoftWraps(false);
    int startOffset = text.indexOf("\t third") - 1;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getEditor().getDocument().deleteString(startOffset, text.length()));


    // Enable soft wraps and ensure that the cache is correctly re-built.
    settings.setUseSoftWraps(true);
    
    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getTextLength());
    type("\n test");
    
    final int offset = getEditor().getDocument().getTextLength() - 1;
    final LogicalPosition logicalPosition = getEditor().offsetToLogicalPosition(offset);
    assertEquals(offset, getEditor().logicalPositionToOffset(logicalPosition));

    final VisualPosition visualPosition = getEditor().offsetToVisualPosition(offset);
    assertEquals(visualPosition, getEditor().logicalToVisualPosition(logicalPosition));
    assertEquals(logicalPosition, getEditor().visualToLogicalPosition(visualPosition));
  }

  public void testSoftWrapsRecalculationOnTabWidthChange() {
    // Inspired by IDEA-78616 - the point is to recalculate soft wraps when tab width is changed.
    String text = 
      "\t<caret> my text";

    // Build soft wraps cache.
    init(40, text);
    
    VisualPosition caretPositionBefore = getEditor().getCaretModel().getVisualPosition();

    // Change tab size.
    final CommonCodeStyleSettings.IndentOptions indentOptions = getCurrentCodeStyleSettings().getIndentOptions(PlainTextFileType.INSTANCE);

    assertNotNull(indentOptions);
    indentOptions.TAB_SIZE++;

    ((EditorImpl)getEditor()).reinitSettings();
    assertEquals(
      new VisualPosition(caretPositionBefore.line, caretPositionBefore.column + 1),
      getEditor().getCaretModel().getVisualPosition()
    );
  }

  public void testNoPreliminarySoftWrapAtLineEnd() {
    // We used to make soft wrap when the string was couple of visual columns before the right screen edge even if it could
    // be completely shown.
    init(52, "a b c", 10);
    assertEmpty(getSoftWrapModel().getRegisteredSoftWraps());
  }

  public void testNoPreliminarySoftWrapBeforeFoldingAtLineEnd() {
    final String text = "a b c test";
    init(71, text, 10);
    addCollapsedFoldRegion(text.indexOf("t"), text.length(), ".");
    assertEmpty(getSoftWrapModel().getRegisteredSoftWraps());
  }

  public void testMultiLineFoldRegionBeforeWrapPosition() {
    final String text =
      "package org.denis;\n" +
      "\n" +
      "\n" +
      "public class BrokenAlignment {\n" +
      "\n" +
      "    void method1(int a) {\n" +
      "    }\n" +
      "\n" +
      "    Object method2(Object ... data) {\n" +
      "        return new Runnable() {\n" +
      "            public void run() {\n" +
      "                System.out.println();\n" +
      "            }\n" +
      "        };\n" +
      "    }\n" +
      "\n" +
      "}";
    init(511, text, JavaFileType.INSTANCE, 10);
    
    addCollapsedFoldRegion(text.indexOf("new Runnable"), text.indexOf("System"), "Runnable() { ");
    
    int start = text.indexOf("System");
    start = text.indexOf("\n", start);
    int end = text.indexOf(';', start);
    addCollapsedFoldRegion(start, end, " }");
    
    final List<? extends SoftWrap> wraps = getSoftWrapModel().getRegisteredSoftWraps();
    assertEquals(1, wraps.size());

    assertEquals(start, wraps.get(0).getStart());
    assertEquals(getEditor().offsetToVisualPosition(start), getEditor().offsetToVisualPosition(start + 1));
    assertEquals(getEditor().offsetToVisualPosition(start).line, getEditor().offsetToVisualPosition(end - 1).line);
  }
  
  public void testEnsureBeforeSoftWrapSignIsVisible() {
    final String text = "a.b.c.d";
    init(61, text, 10);
    
    checkSoftWraps(text.indexOf('c') + 1);
  }

  public void testWrapAfterCollapsedFoldRegion() {
    final String text =
      "this is a long text to fold more";
    init(12, text);
    
    int start = text.indexOf("long");
    int end = text.indexOf("more");
    addCollapsedFoldRegion(start, end, "placeholder which is long enough to be wrapped");
    
    checkSoftWraps(start, end);
  }

  public void testFoldRegionWithLongPlaceholderText() {
    final String text =
      "this is a string with(fold region), end)\n" +
      "second string";
    init(40, text);

    int start = text.indexOf("fold");
    int end = text.indexOf(',');
    addCollapsedFoldRegion(start, end, "this is a really long fold region placeholder");
    
    checkSoftWraps(start, end);
    assertEquals(new VisualPosition(3, 0), getEditor().offsetToVisualPosition(text.indexOf("second")));
  }

  public void testDeleteWhenCaretBeforeSoftWrap() {
    final String text =
      "text 1234";
    init(7, text);

    final int offset = text.indexOf("123");
    checkSoftWraps(offset);

    final CaretModel caretModel = getEditor().getCaretModel();
    caretModel.moveToOffset(offset);
    caretModel.moveCaretRelatively(-1, 0, false, false, false);
    assertEquals(offset, caretModel.getOffset()); // Navigating from 'after soft wrap' to the 'before soft wrap' position.
    delete();
    assertEquals("text 234", getEditor().getDocument().getText());
  }

  public void testSelectionOfLineWithSoftWrapAndFoldRegion() {
    final String text =
      "123\n" +
      "fold line 1\n" +
      "fold line 2 456";
    init(6, text);
    addCollapsedFoldRegion(2, text.indexOf("4"), "...");
    getEditor().getSelectionModel().selectLineAtCaret();
    assertEquals(text, getEditor().getSelectionModel().getSelectedText());
  }
  
  public void testCaretPositionAfterLineRemoval() {
    final String text =
      "123456\n" +
      "123456\n" +
      "line towrapbecauseitislong\n" +
      "923456";
    init(8, text);
    final CaretModel caretModel = getEditor().getCaretModel();
    caretModel.moveToOffset(text.indexOf("e"));
    final VisualPosition position = caretModel.getVisualPosition();
    deleteLine();
    assertEquals(text.substring(0, text.indexOf("line")) + text.substring(text.indexOf('9')), getEditor().getDocument().getText());
    assertEquals(position, caretModel.getVisualPosition());
  }

  public void testNoUnnecessaryHorizontalScrollBar() {
    // Inspired by IDEA-87184
    final String text = "12345678 abcdefgh";
    init(15, 10, text);
    getEditor().getCaretModel().moveToOffset(text.length());
    backspace();
    verifySoftWrapPositions(9);
  }

  public void testCaretInsideFoldRegionOnCollapse() {
    // IDEA-89874
    String text = "one two three";
    init(30, text);
    int foldStart = text.indexOf("two");
    addFoldRegion(foldStart, foldStart + "two".length(), "...");
    getEditor().getCaretModel().moveToOffset(foldStart + 1);
    FoldRegion foldRegion = getFoldRegion(foldStart);
    assertNotNull(foldRegion);
    assertTrue(foldRegion.isExpanded());
    
    toggleFoldRegionState(foldRegion, false);

    assertFalse(foldRegion.isExpanded());
    assertEquals(foldStart, getEditor().getCaretModel().getOffset());
  }

  public void testFoldRegionEndingAtLineStart() {
    init(100, "aaa\nbbb\nccc\nddd");
    addCollapsedFoldRegion(4, 8, "...");
    addCollapsedFoldRegion(13, 15, "...");

    WriteCommandAction.runWriteCommandAction(getProject(), () -> getEditor().getDocument().insertString(10, "C"));


    // verify that cached layout data is intact after document change and position recalculation is done correctly
    assertEquals(new LogicalPosition(0, 0), getEditor().visualToLogicalPosition(new VisualPosition(0, 0)));
  }
  
  public void testOnlyMinimalRangeIsRecalculatedOnDocumentChange() {
    init("aa bb cc dd ee ff<caret> gg hh ii jj", PlainTextFileType.INSTANCE);
    EditorTestUtil.configureSoftWraps(getEditor(), 8);
    verifySoftWrapPositions(8, 15, 21);
    
    final IncrementalCacheUpdateEvent[] event = new IncrementalCacheUpdateEvent[1];
    ((SoftWrapModelImpl)getEditor().getSoftWrapModel()).getApplianceManager().addListener(new SoftWrapAwareDocumentParsingListenerAdapter() {
      @Override
      public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent e) {
        assertNull(event[0]);
        event[0] = e;
      }
    });
    
    type(' ');
    
    verifySoftWrapPositions(8, 15, 22);
    assertNotNull(event[0]);
    assertEquals(8, event[0].getStartOffset());
    assertEquals(22, event[0].getActualEndOffset());
  }
  
  public void testPositionsAreCorrectAfterIncrementalRecalculation() {
    initText("abra<caret> cadabra");
    configureSoftWraps(10);
    type(' ');
    
    assertEquals(new LogicalPosition(0, 6), getEditor().offsetToLogicalPosition(6));
    assertEquals(new VisualPosition(1, 1), getEditor().offsetToVisualPosition(6));
  }

  public void testSoftWrappingWithFoldRegionAndTabs() {
    initText("foldA\t\t\t\t");
    addCollapsedFoldRegion(0, 4, ".");
    configureSoftWraps(10);

    assertNull(getEditor().getSoftWrapModel().getSoftWrap(4));
  }
  
  public void testSoftWrapsAreNotCreatedInsideFoldRegions() {
    initText("\t\t\taaaaa\t");
    addCollapsedFoldRegion(7, 9, ".");
    configureSoftWraps(10);

    assertNull(getEditor().getSoftWrapModel().getSoftWrap(8));
  }
  
  public void testUnbreakableLinesDontAffectFollowingLines() {
    initText("unbreakableLine\nshort line");
    configureSoftWraps(10);
    
    verifySoftWrapPositions(10);
  }
  
  public void testFoldRegionPreventsLaterWrapping() {
    initText("unbreakableText.txt");
    configureSoftWraps(10);
    verifySoftWrapPositions(10);
    
    addCollapsedFoldRegion(12, 13, ".");

    verifySoftWrapPositions(10);
    assertEquals(1, getEditor().offsetToVisualPosition(19).line);
  }
  
  public void testMoveWithFoldRegionInside() {
    initText("abc\ndef\nghi\n");
    configureSoftWraps(100);
    addCollapsedFoldRegion(0, 4, "...");

    WriteCommandAction.runWriteCommandAction(getProject(), () -> ((DocumentEx)getEditor().getDocument()).moveText(0, 4, 12));


    assertEquals(new LogicalPosition(2, 0), getEditor().visualToLogicalPosition(new VisualPosition(2, 1)));
  }

  private static final int DEFAULT_SYMBOL_WIDTH_PX = 10;
  private void init(final int visibleWidthInColumns, @NotNull String fileText) {
    init(visibleWidthInColumns, 10, fileText);
  }

  private void init(final int visibleWidthInColumns, @NotNull String fileText, boolean useCustomSoftWrapIndent) {
    init(visibleWidthInColumns * DEFAULT_SYMBOL_WIDTH_PX, fileText, PlainTextFileType.INSTANCE, DEFAULT_SYMBOL_WIDTH_PX, useCustomSoftWrapIndent);
  }

  private void init(final int visibleWidthInColumns, final int symbolWidthInPixels, @NotNull String fileText) {
    init(visibleWidthInColumns * symbolWidthInPixels, fileText, symbolWidthInPixels);
  }
  
  private void init(final int visibleWidth, @NotNull String fileText, int symbolWidth) {
    init(visibleWidth, fileText, PlainTextFileType.INSTANCE, symbolWidth);
  }

  private void init(final int visibleWidth, @NotNull String fileText, @NotNull FileType fileType, final int symbolWidth) {
    init(visibleWidth, fileText, fileType, symbolWidth, true);
  }

  private void init(final int visibleWidth, @NotNull String fileText, @NotNull FileType fileType, final int symbolWidth, boolean useCustomSoftWrapIndent) {
    init(fileText, fileType);
    EditorTestUtil.configureSoftWraps(getEditor(), visibleWidth, 1000, symbolWidth, useCustomSoftWrapIndent);
  }

  private void checkSoftWraps(int... startOffsets) {
    assertArrayEquals(startOffsets, getSoftWrapModel().getRegisteredSoftWraps().stream().mapToInt(s -> s.getStart()).toArray());
  }
  
  private SoftWrapModelImpl getSoftWrapModel() {
    return (SoftWrapModelImpl)getEditor().getSoftWrapModel();
  }
}
