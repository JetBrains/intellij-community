// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.editor.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeHighlighting.Pass;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.FileEditorManagerTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.tabs.TabInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;


import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.fileEditorManagerFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaFileEditorManagerTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<FileEditorManagerImpl> managerFixture = fileEditorManagerFixture(projectFixture, false);

  @Test
  public void testAsyncOpening() {
    @Language("XML")
    String FEM_XML = """
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
        </component>""";
    FileEditorManagerImpl manager = managerFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> FileEditorManagerTestUtil.openFiles(manager, getTestDataPath(), FEM_XML));
  }

  @Test
  public void testFoldingIsNotBlinkingOnNavigationToSingleLineMethod() {
    Project project = projectFixture.get();
    Module module = codeInsightFixture.get().getModule();
    FileEditorManagerImpl manager = managerFixture.get();
    VirtualFile dir = FileEditorManagerTestUtil.getFile(getTestDataPath(), "/src");
    PsiTestUtil.addSourceContentToRoots(module, dir);
    try {
      VirtualFile file = FileEditorManagerTestUtil.getFile(getTestDataPath(), "/src/Bar.java");
      PsiJavaFile psiFile = ReadAction.computeBlocking(() -> (PsiJavaFile)PsiManager.getInstance(project).findFile(file));
      assertNotNull(psiFile);
      PsiMethod method = ReadAction.computeBlocking(() -> psiFile.getClasses()[0].getMethods()[0]);
      FileEditor[] editors = EdtTestUtil.runInEdtAndGet(() -> {
        method.navigate(true);
        return manager.getEditors(file);
      });

      assertEquals(1, editors.length);
      Editor editor = ((TextEditor)editors[0]).getEditor();
      FoldRegion[] regions = EdtTestUtil.runInEdtAndGet(() -> {
        EditorTestUtil.waitForLoading(editor);
        return editor.getFoldingModel().getAllFoldRegions();
      });
      assertEquals(2, regions.length);
      assertTrue(regions[0].isExpanded());
      assertTrue(regions[1].isExpanded());

      regions = EdtTestUtil.runInEdtAndGet(() -> {
        CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, new int[]{Pass.UPDATE_ALL, Pass.LOCAL_INSPECTIONS}, false);
        return editor.getFoldingModel().getAllFoldRegions();
      });
      assertEquals(2, regions.length);
      assertTrue(regions[0].isExpanded());
      assertTrue(regions[1].isExpanded());
    }
    finally {
      PsiTestUtil.removeSourceRoot(module, dir);
    }
  }

  @Test
  public void testOpenModuleDescriptorFile() {
    Project project = projectFixture.get();
    FileEditorManagerImpl manager = managerFixture.get();
    VirtualFile moduleInfoFile = FileEditorManagerTestUtil.getFile(getTestDataPath(), "/src/module-info.java");
    assertNotNull(moduleInfoFile);

    EditorTabbedContainer openedTabPane = EdtTestUtil.runInEdtAndGet(() -> {
      manager.openFile(moduleInfoFile);
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project);
      return manager.getCurrentWindow().getTabbedPane();
    });

    assertEquals(1, openedTabPane.getTabCount());
    TabInfo firstTab = openedTabPane.getTabs().getTabAt(0);
    assertEquals("module-info.java (test.module)", firstTab.getText());
  }

  private static String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/fileEditorManager";
  }
}
