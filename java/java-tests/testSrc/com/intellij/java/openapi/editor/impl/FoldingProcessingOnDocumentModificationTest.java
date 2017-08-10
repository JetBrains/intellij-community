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
package com.intellij.java.openapi.editor.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.TestFileType;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author Denis Zhdanov
 * @since 11/18/10 7:42 PM
 */
public class FoldingProcessingOnDocumentModificationTest extends AbstractEditorTest {
  
  public void testUnexpectedClassLevelJavadocExpandingOnClassSignatureChange() throws IOException {
    // Inspired by IDEA-61275

    String text =
      "/**\n" +
      " * This is a test comment\n" +
      " */\n" +
      "public <caret>class Test {\n" +
      "}";
    init(text, TestFileType.JAVA);

    CaretModel caretModel = myEditor.getCaretModel();
    int caretOffset = caretModel.getOffset();
    
    assertEquals(caretOffset, caretModel.getOffset());

    updateFoldRegions();
    toggleFoldRegionState(getFoldRegion(0), false);
    type('a');
    updateFoldRegions();

    assertEquals(caretOffset + 1, caretModel.getOffset());
    assertEquals(1, myEditor.getFoldingModel().getAllFoldRegions().length);
    FoldRegion foldRegion = getFoldRegion(0);
    assertFalse(foldRegion.isExpanded());
  }
  
  public void testCollapseAllHappensBeforeFirstCodeFoldingPass() throws Exception {
    init("class Foo {\n" +
         "    void m() {\n" +
         "        System.out.println();\n" +
         "        System.out.println();\n" +
         "    }\n" +
         "}", TestFileType.JAVA);
    
    buildInitialFoldRegions();
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    runFoldingPass(true);
    assertEquals(1, myEditor.getFoldingModel().getAllFoldRegions().length);
  }
  
  public void testSurvivingBrokenPsi() throws Exception {
    openJavaEditor("class Foo {\n" +
                   "    void m() {\n" +
                   "\n" +
                   "    }\n" +
                   "}");
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        myEditor.getDocument().insertString(0, "/*");
      }
    }.execute().throwException();

    checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             () -> myEditor.getDocument().deleteString(0, 2));

    checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");
  }
  
  public void testInvalidRegionIsRemovedOnExpanding() throws Exception {
    openJavaEditor("class Foo {\n" +
                   "    void m() {\n" +
                   "\n" +
                   "    }\n" +
                   "}");
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        myEditor.getDocument().insertString(0, "/*");
      }
    }.execute().throwException();

    checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

    executeAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);
    checkFoldingState("[FoldRegion -(0:37), placeholder='/.../']");
  }
  
  public void testEditingNearRegionExpandsIt() throws Exception {
    openJavaEditor("class Foo {\n" +
                   "    void m() <caret>{\n" +
                   "\n" +
                   "    }\n" +
                   "}");
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    executeAction(IdeActions.ACTION_EDITOR_DELETE);
    checkFoldingState("[]");
  }
  
  private void openJavaEditor(String text) throws Exception {
    init(text, TestFileType.JAVA);
    buildInitialFoldRegions();
    runFoldingPass(true);
    runFoldingPass();
  }

  private static void checkFoldingState(String expectedState) {
    PsiDocumentManager.getInstance(ourProject).commitDocument(myEditor.getDocument());
    runFoldingPass();
    assertEquals(expectedState, Arrays.toString(myEditor.getFoldingModel().getAllFoldRegions()));
  }
  
  private static void buildInitialFoldRegions() {
    CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(myEditor);
  }
  
  private static void updateFoldRegions() {
    CodeFoldingManager.getInstance(getProject()).updateFoldRegions(myEditor);
  }
  
  private static void runFoldingPass() {
    runFoldingPass(false);
  }
  
  private static void runFoldingPass(boolean firstTime) {
    Runnable runnable = CodeFoldingManager.getInstance(getProject()).updateFoldRegionsAsync(myEditor, firstTime);
    assertNotNull(runnable);
    runnable.run();
  }
}
