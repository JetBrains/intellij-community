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
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Denis Zhdanov
 */
public class FoldingProcessingOnDocumentModificationTest extends AbstractEditorTest {
  
  public void testUnexpectedClassLevelJavadocExpandingOnClassSignatureChange() {
    // Inspired by IDEA-61275

    String text =
      """
        /**
         * This is a test comment
         */
        public <caret>class Test {
        }""";
    init(text, JavaFileType.INSTANCE);

    CaretModel caretModel = getEditor().getCaretModel();
    int caretOffset = caretModel.getOffset();
    
    assertEquals(caretOffset, caretModel.getOffset());

    updateFoldRegions();
    toggleFoldRegionState(getFoldRegion(0), false);
    type('a');
    updateFoldRegions();

    assertEquals(caretOffset + 1, caretModel.getOffset());
    assertEquals(1, getEditor().getFoldingModel().getAllFoldRegions().length);
    FoldRegion foldRegion = getFoldRegion(0);
    assertFalse(foldRegion.isExpanded());
  }
  
  public void testCollapseAllHappensBeforeFirstCodeFoldingPass() {
    init("""
           class Foo {
               void m() {
                   System.out.println();
                   System.out.println();
               }
           }""", JavaFileType.INSTANCE);
    
    buildInitialFoldRegions();
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    runFoldingPass(true);
    assertEquals(1, getEditor().getFoldingModel().getAllFoldRegions().length);
  }
  
  public void testSurvivingBrokenPsi() {
    openJavaEditor("""
                     class Foo {
                         void m() {

                         }
                     }""");
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

    WriteCommandAction.writeCommandAction(getProject()).run(() -> getEditor().getDocument().insertString(0, "/*"));

    checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             () -> getEditor().getDocument().deleteString(0, 2));

    checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");
  }
  
  public void testInvalidRegionIsRemovedOnExpanding() {
    openJavaEditor("""
                     class Foo {
                         void m() {

                         }
                     }""");
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

    WriteCommandAction.writeCommandAction(getProject()).run(() -> getEditor().getDocument().insertString(0, "/*"));

    checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

    executeAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);
    checkFoldingState("[FoldRegion -(0:37), placeholder='/.../']");
  }
  
  public void testEditingNearRegionExpandsIt() {
    openJavaEditor("""
                     class Foo {
                         void m() <caret>{

                         }
                     }""");
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    executeAction(IdeActions.ACTION_EDITOR_DELETE);
    checkFoldingState("[]");
  }

  public void testCollapsedFoldRegionIsUpdatedIfPlaceholderIsChanging() {
    int[] valuePlaceholder = new int[] {0};
    runWithCustomFoldingBuilder(new TestFoldingBuilder() {
      @NotNull
      @Override
      public String getPlaceholderText(@NotNull ASTNode node) {
        return Integer.toString(valuePlaceholder[0]);
      }

      @Override
      public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
      }
    }, () -> {
      openEditor("<caret>\nvalue", PlainTextFileType.INSTANCE);
      checkFoldingState("[FoldRegion +(1:6), placeholder='0']");
      valuePlaceholder[0] = 1;
      runFoldingPass();
      checkFoldingState("[FoldRegion +(1:6), placeholder='1']");
    });
  }

  public void testRegionIsUnfoldedIfPlaceholderAndCollapsedByDefaultAreChangingAtTheSameTime() {
    AtomicBoolean setting = new AtomicBoolean(true);
    runWithCustomFoldingBuilder(new TestFoldingBuilder() {
      @NotNull
      @Override
      public String getPlaceholderText(@NotNull ASTNode node) {
        return setting.get() ? "foo" : "bar";
      }

      @Override
      public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return setting.get();
      }
    }, () -> {
      openEditor("<caret>\nvalue", PlainTextFileType.INSTANCE);
      checkFoldingState("[FoldRegion +(1:6), placeholder='foo']");
      setting.set(false);
      runFoldingPass();
      checkFoldingState("[FoldRegion -(1:6), placeholder='bar']");
    });
  }

  private static void runWithCustomFoldingBuilder(FoldingBuilder builder, Runnable task) {
    LanguageFolding.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, builder);
    try {
      task.run();
    }
    finally {
      LanguageFolding.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, builder);
    }
  }

  private void openJavaEditor(String text) {
    openEditor(text, JavaFileType.INSTANCE);
  }

  private void openEditor(String text, FileType fileType) {
    init(text, fileType);
    buildInitialFoldRegions();
    runFoldingPass(true);
    runFoldingPass();
  }

  private void checkFoldingState(String expectedState) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(getEditor().getDocument());
    runFoldingPass();
    assertEquals(expectedState, Arrays.toString(getEditor().getFoldingModel().getAllFoldRegions()));
  }
  
  private void buildInitialFoldRegions() {
    CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(getEditor());
  }
  
  private void updateFoldRegions() {
    CodeFoldingManager.getInstance(getProject()).updateFoldRegions(getEditor());
  }
  
  private void runFoldingPass() {
    runFoldingPass(false);
  }
  
  private void runFoldingPass(boolean firstTime) {
    Runnable runnable = CodeFoldingManager.getInstance(getProject()).updateFoldRegionsAsync(getEditor(), firstTime);
    assertNotNull(runnable);
    runnable.run();
  }

  private static abstract class TestFoldingBuilder implements FoldingBuilder {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
      int pos = document.getText().indexOf("value");
      if (pos >= 0) {
        return new FoldingDescriptor[] {new FoldingDescriptor(node, new TextRange(pos, pos + 5), null,
                                                              Collections.singleton(ModificationTracker.EVER_CHANGED))};
      }
      return FoldingDescriptor.EMPTY_ARRAY;
    }
  }
}
