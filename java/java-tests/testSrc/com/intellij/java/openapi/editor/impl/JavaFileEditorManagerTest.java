// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.editor.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * @author Dmitry Avdeev
 */
public class JavaFileEditorManagerTest extends FileEditorManagerTestCase {

  public void testAsyncOpening() throws JDOMException, ExecutionException, InterruptedException, IOException {
    openFiles("<component name=\"FileEditorManager\">\n" +
              "    <leaf>\n" +
              "      <file pinned=\"false\" current=\"true\" current-in-tab=\"true\">\n" +
              "        <entry file=\"file://$PROJECT_DIR$/src/Bar.java\">\n" +
              "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
              "            <state vertical-scroll-proportion=\"0.032882012\" vertical-offset=\"0\" max-vertical-offset=\"517\">\n" +
              "              <caret line=\"1\" column=\"26\" selection-start=\"45\" selection-end=\"45\" />\n" +
              "              <folding>\n" +
              "                <element signature=\"e#69#70#0\" expanded=\"true\" />\n" +
              "              </folding>\n" +
              "            </state>\n" +
              "          </provider>\n" +
              "        </entry>\n" +
              "      </file>\n" +
              "    </leaf>\n" +
              "  </component>");
  }

  public void testFoldingIsNotBlinkingOnNavigationToSingleLineMethod() {
    VirtualFile file = getFile("/src/Bar.java");
    PsiJavaFile psiFile = (PsiJavaFile)getPsiManager().findFile(file);
    assertNotNull(psiFile);
    PsiMethod method = psiFile.getClasses()[0].getMethods()[0];
    method.navigate(true);

    FileEditor[] editors = myManager.getEditors(file);
    assertEquals(1, editors.length);
    Editor editor = ((TextEditor)editors[0]).getEditor();
    EditorTestUtil.waitForLoading(editor);
    FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    assertEquals(2, regions.length);
    assertTrue(regions[0].isExpanded());
    assertTrue(regions[1].isExpanded());

    CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, new int[]{Pass.UPDATE_ALL, Pass.LOCAL_INSPECTIONS}, false);

    regions = editor.getFoldingModel().getAllFoldRegions();
    assertEquals(2, regions.length);
    assertTrue(regions[0].isExpanded());
    assertTrue(regions[1].isExpanded());
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-tests/testData/fileEditorManager";
  }
}
