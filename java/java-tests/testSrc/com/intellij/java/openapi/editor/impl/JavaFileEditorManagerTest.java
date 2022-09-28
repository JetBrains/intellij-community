// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.editor.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.ui.tabs.TabInfo;

import java.io.File;

public class JavaFileEditorManagerTest extends FileEditorManagerTestCase {
  public void testAsyncOpening() {
    openFiles("""
                <component name="FileEditorManager">
                    <leaf>
                      <file pinned="false" current="true" current-in-tab="true">
                        <entry file="file://$PROJECT_DIR$/src/Bar.java">
                          <provider selected="true" editor-type-id="text-editor">
                            <state vertical-scroll-proportion="0.032882012" vertical-offset="0" max-vertical-offset="517">
                              <caret line="1" column="26" selection-start="45" selection-end="45" />
                              <folding>
                                <element signature="e#69#70#0" expanded="true" />
                              </folding>
                            </state>
                          </provider>
                        </entry>
                      </file>
                    </leaf>
                  </component>""");
  }

  public void testFoldingIsNotBlinkingOnNavigationToSingleLineMethod() {
    VirtualFile file = getFile("/src/Bar.java");
    PsiJavaFile psiFile = (PsiJavaFile)getPsiManager().findFile(file);
    assertNotNull(psiFile);
    PsiMethod method = psiFile.getClasses()[0].getMethods()[0];
    method.navigate(true);

    FileEditor[] editors = manager.getEditors(file);
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

  public void testOpenModuleDescriptorFile() {
    VirtualFile moduleInfoFile = getFile("/src/module-info.java");
    assertNotNull(moduleInfoFile);

    manager.openFile(moduleInfoFile, false);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

    EditorTabbedContainer openedTabPane = manager.getCurrentWindow().getTabbedPane();
    assertEquals(1, openedTabPane.getTabCount());
    TabInfo firstTab = openedTabPane.getTabs().getTabAt(0);
    assertEquals("module-info.java (test.module)", firstTab.getText());
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-tests/testData/fileEditorManager";
  }
}
