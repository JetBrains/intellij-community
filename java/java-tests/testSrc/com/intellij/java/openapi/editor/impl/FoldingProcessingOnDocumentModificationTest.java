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
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class FoldingProcessingOnDocumentModificationTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testUnexpectedClassLevelJavadocExpandingOnClassSignatureChange() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-61275

      String text =
        """
          /**
           * This is a test comment
           */
          public <caret>class Test {
          }""";
      myFixture.init(text, JavaFileType.INSTANCE);

      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      int caretOffset = caretModel.getOffset();
    
      assertEquals(caretOffset, caretModel.getOffset());

      updateFoldRegions();
      myFixture.toggleFoldRegionState(myFixture.getFoldRegion(0), false);
      myFixture.type('a');
      updateFoldRegions();

      assertEquals(caretOffset + 1, caretModel.getOffset());
      assertEquals(1, myFixture.getEditor().getFoldingModel().getAllFoldRegions().length);
      FoldRegion foldRegion = myFixture.getFoldRegion(0);
      assertFalse(foldRegion.isExpanded());
    });
  }
  
  @Test
  public void testCollapseAllHappensBeforeFirstCodeFoldingPass() {
    myFixture.runTest(() -> {
      myFixture.init("""
             class Foo {
                 void m() {
                     System.out.println();
                     System.out.println();
                 }
             }""", JavaFileType.INSTANCE);

      EditorTestUtil.buildInitialFoldingsInBackground(myFixture.getEditor());
      myFixture.executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
      runFoldingPass(true);
      assertEquals(1, myFixture.getEditor().getFoldingModel().getAllFoldRegions().length);
    });
  }
  
  @Test
  public void testSurvivingBrokenPsi() {
    myFixture.runTest(() -> {
      openJavaEditor("""
                       class Foo {
                           void m() {

                           }
                       }""");
      myFixture.executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
      checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

      WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> myFixture.getEditor().getDocument().insertString(0, "/*"));

      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                               () -> myFixture.getEditor().getDocument().deleteString(0, 2));

      checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");
    });
  }
  
  @Test
  public void testInvalidRegionIsRemovedOnExpanding() {
    myFixture.runTest(() -> {
      openJavaEditor("""
                       class Foo {
                           void m() {

                           }
                       }""");
      myFixture.executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
      checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

      WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> myFixture.getEditor().getDocument().insertString(0, "/*"));

      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

      myFixture.executeAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);
      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../']");
    });
  }
  
  @Test
  public void testEditingNearRegionExpandsIt() {
    myFixture.runTest(() -> {
      openJavaEditor("""
                       class Foo {
                           void m() <caret>{

                           }
                       }""");
      myFixture.executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE);
      checkFoldingState("[]");
    });
  }

  @Test
  public void testCollapsedFoldRegionIsUpdatedIfPlaceholderIsChanging() {
    myFixture.runTest(() -> {
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
    });
  }

  @Test
  public void testRegionIsUnfoldedIfPlaceholderAndCollapsedByDefaultAreChangingAtTheSameTime() {
    myFixture.runTest(() -> {
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
    myFixture.init(text, fileType);
    runFoldingPass(true);
    runFoldingPass();
  }

  private void checkFoldingState(String expectedState) {
    PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(myFixture.getEditor().getDocument());
    runFoldingPass();
    assertEquals(expectedState, Arrays.toString(myFixture.getEditor().getFoldingModel().getAllFoldRegions()));
  }

  private void updateFoldRegions() {
    CodeFoldingManager.getInstance(myFixture.getProject()).updateFoldRegions(myFixture.getEditor());
  }
  
  private void runFoldingPass() {
    runFoldingPass(false);
  }

  private void runFoldingPass(boolean firstTime) {
    ThreadingAssertions.assertEventDispatchThread();
    Runnable runnable;
    try {
      runnable = ReadAction.nonBlocking(
          () -> CodeFoldingManager.getInstance(myFixture.getProject()).updateFoldRegionsAsync(myFixture.getEditor(), firstTime))
        .submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
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
