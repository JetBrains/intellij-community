// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project;

import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;

import java.nio.file.Path;

import static com.intellij.testFramework.RunAll.runAll;

public class LoadProjectTest extends HeavyPlatformTestCase {
  @Override
  protected void setUpProject() throws Exception {
    String projectPath = PathManagerEx.getTestDataPath() + "/model/model.ipr";
    myProject = PlatformTestUtil.loadAndOpenProject(Path.of(projectPath), getTestRootDisposable());
    ServiceContainerUtil.registerServiceInstance(myProject, FileEditorManager.class, new FileEditorManagerImpl(myProject, ((ComponentManagerEx)myProject).getCoroutineScope()));
  }

  @Override
  protected void tearDown() {
    Project project = myProject;
    myProject = null;

    runAll(
      () -> ((FileEditorManagerEx)FileEditorManager.getInstance(project)).closeAllFiles(),
      () -> ProjectManagerEx.getInstanceEx().forceCloseProject(project),
      () -> super.tearDown(),
      () -> checkNoPsiFilesInProjectReachable(project)
    );
  }

  private static void checkNoPsiFilesInProjectReachable(Project project) {
    LeakHunter.checkLeak(ApplicationManager.getApplication(), PsiFileImpl.class,
                         psiFile -> psiFile.getViewProvider().getVirtualFile().getFileSystem() instanceof LocalFileSystem &&
                                    psiFile.getProject() == project);
  }

  public void testLoadProject() {
    VirtualFile src = ProjectRootManager.getInstance(getProject()).getContentSourceRoots()[0];

    VirtualFile a = src.findFileByRelativePath("/x/AClass.java");
    assertNotNull(a);
    PsiFile fileA = getPsiManager().findFile(a);
    assertNotNull(fileA);
    fileA.navigate(true);
    Editor editorA = FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), a), true);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    EditorTestUtil.waitForLoading(editorA);

    assertNotNull(editorA);
    CodeInsightTestFixtureImpl.instantiateAndRun(fileA, editorA, new int[] {Pass.EXTERNAL_TOOLS}, false);

    VirtualFile b = src.findFileByRelativePath("/x/BClass.java");
    assertNotNull(b);
    PsiFile fileB = getPsiManager().findFile(b);
    assertNotNull(fileB);
    fileB.navigate(true);
    Editor editorB = FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), b), true);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertNotNull(editorB);
    CodeInsightTestFixtureImpl.instantiateAndRun(fileB, editorB, new int[]{Pass.EXTERNAL_TOOLS}, false);

    FileEditor[] allEditors = FileEditorManager.getInstance(getProject()).getAllEditors();
    assertEquals(2, allEditors.length);
  }
}
