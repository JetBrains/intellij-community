/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.codeInsight;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class BlockSelectionEditingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testBlockRemovalAndCollapsedFoldRegionsBefore() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-69371
      String initialText =
        """
          fold line #1
          fold line #2
          initialText    line 1
          initialText    line 2
          initialText    line 3""";

      myFixture.init(initialText, PlainTextFileType.INSTANCE);
      final int foldEndOffset = initialText.indexOf("initialText");
      myFixture.addCollapsedFoldRegion(0, foldEndOffset, "...");
      int column = "initialText".length();
      final LogicalPosition blockStart = new LogicalPosition(3, column);
      final LogicalPosition blockEnd = new LogicalPosition(4, column);
      final SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();
      selectionModel.setBlockSelection(blockStart, blockEnd);
      myFixture.delete();
      myFixture.delete();

      String expectedText =
        """
          fold line #1
          fold line #2
          initialText    line 1
          initialText  line 2
          initialText  line 3""";
      assertEquals(expectedText, myFixture.getEditor().getDocument().getText());
      myFixture.assertSelectionRanges(new int[][]{{59, 59}, {79, 79}});
      final FoldRegion foldRegion = myFixture.getFoldRegion(0);
      assertNotNull(foldRegion);
      assertEquals(0, foldRegion.getStartOffset());
      assertEquals(foldEndOffset, foldRegion.getEndOffset());
    });
  }

  @Test
  public void testBlockSelectionAndCollapsedFolding() {
    myFixture.runTest(() -> {
      String text =
        """
          class Test {
              private class Inner1 {
                  int i;
              }

              private class Inner2 {
                  int i;
              }
          }""";
      myFixture.init(text, JavaFileType.INSTANCE);

      int foldStart1 = text.indexOf("Inner1");
      foldStart1 = text.indexOf('{', foldStart1);
      int foldEnd1 = text.indexOf('}', foldStart1) + 1;
      myFixture.addCollapsedFoldRegion(foldStart1, foldEnd1, "...");

      int foldStart2 = text.indexOf('{', foldEnd1);
      int foldEnd2 = text.indexOf('}', foldStart2) + 1;
      myFixture.addCollapsedFoldRegion(foldStart2, foldEnd2, "...");

      LogicalPosition blockStart = new LogicalPosition(1, 4);
      LogicalPosition blockEnd = new LogicalPosition(5, 5 + "private".length());
      myFixture.getEditor().getSelectionModel().setBlockSelection(blockStart, blockEnd);

      assertTrue(myFixture.getFoldRegion(foldStart1).isExpanded());
      assertFalse(myFixture.getFoldRegion(foldStart2).isExpanded());
    });
  }

  @Test
  public void testBlockSelectionOnEmptyFile() {
    myFixture.runTest(() -> {
      myFixture.initText("");

      myFixture.getEditor().getSelectionModel().setBlockSelection(new LogicalPosition(0, 0), new LogicalPosition(0, 0));

      assertArrayEquals(new int[]{0}, myFixture.getEditor().getSelectionModel().getBlockSelectionStarts());
      assertArrayEquals(new int[]{0}, myFixture.getEditor().getSelectionModel().getBlockSelectionEnds());
    });
  }
}
