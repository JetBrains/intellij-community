// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.project;

import com.intellij.JavaTestUtil;
import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.intellij.testFramework.RunAll.runAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LoadProjectTest {
  private Disposable myProjectDisposable;
  private Project myProject;

  @BeforeEach
  void setUp() {
    myProjectDisposable = Disposer.newDisposable("LoadProjectTest");
    String projectPath = JavaTestUtil.getJavaTestDataPath() + "/model/model.ipr";
    myProject = PlatformTestUtil.loadAndOpenProject(Path.of(projectPath), myProjectDisposable);
    FileEditorManagerImpl editorManager = new FileEditorManagerImpl(myProject, ((ComponentManagerEx)myProject).getCoroutineScope());
    ServiceContainerUtil.replaceService(myProject, FileEditorManager.class, editorManager, myProjectDisposable);
  }

  @AfterEach
  void tearDown() {
    Project project = myProject;
    if (project == null) {
      EdtTestUtil.runInEdtAndWait(() -> Disposer.dispose(myProjectDisposable));
      return;
    }

    runAll(
      () -> EdtTestUtil.runInEdtAndWait(() -> ((FileEditorManagerEx)FileEditorManager.getInstance(project)).closeAllFiles()),
      () -> EdtTestUtil.runInEdtAndWait(() -> Disposer.dispose(myProjectDisposable)),
      () -> checkNoPsiFilesInProjectReachable(project)
    );
  }

  private static void checkNoPsiFilesInProjectReachable(Project project) {
    LeakHunter.checkLeak(ApplicationManager.getApplication(), PsiFileImpl.class,
                         psiFile -> psiFile.getViewProvider().getVirtualFile().isInLocalFileSystem() &&
                                    psiFile.getProject() == project);
  }

  @Test
  public void testLoadProject() {
    VirtualFile src = ProjectRootManager.getInstance(myProject).getContentSourceRoots()[0];

    VirtualFile a = src.findFileByRelativePath("/x/AClass.java");
    assertNotNull(a);
    PsiFile fileA = ReadAction.computeBlocking(() -> PsiManager.getInstance(myProject).findFile(a));
    assertNotNull(fileA);
    EdtTestUtil.runInEdtAndWait(() -> {
      fileA.navigate(true);
      Editor editorA = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, a), true);
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      EditorTestUtil.waitForLoading(editorA);

      assertNotNull(editorA);
      CodeInsightTestFixtureImpl.instantiateAndRun(fileA, editorA, new int[] {Pass.EXTERNAL_TOOLS}, false);
    });

    VirtualFile b = src.findFileByRelativePath("/x/BClass.java");
    assertNotNull(b);
    PsiFile fileB = ReadAction.computeBlocking(() -> PsiManager.getInstance(myProject).findFile(b));
    assertNotNull(fileB);
    EdtTestUtil.runInEdtAndWait(() -> {
      fileB.navigate(true);
      Editor editorB = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, b), true);
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      assertNotNull(editorB);
      CodeInsightTestFixtureImpl.instantiateAndRun(fileB, editorB, new int[]{Pass.EXTERNAL_TOOLS}, false);
    });

    FileEditor[] allEditors = FileEditorManager.getInstance(myProject).getAllEditors();
    assertEquals(2, allEditors.length);
  }
}
