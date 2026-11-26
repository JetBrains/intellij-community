// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyTestHelper;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.nio.file.Path;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.disposableFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class CopyPasteHeavyTest {
  @SuppressWarnings("deprecation")
  private final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<Disposable> disposableFixture = disposableFixture();
  private final TestFixture<Path> structureDirFixture = tempPathFixture();

  @Test
  public void testMoveFileFromTheOtherProjectDoesntCrash() {
    Project project = projectFixture.get();
    Path tempPath = TemporaryDirectory.generateTemporaryPath("second");
    Project alienProject =
      EdtTestUtil.runInEdtAndGet(() -> PlatformTestUtil.loadAndOpenProject(tempPath.resolve("alienProj.ipr"), disposableFixture.get()));
    Path alienModuleFile = ProjectKt.getStateStore(alienProject).getProjectBasePath().resolve("x.iml");
    Module alienModule =
      WriteAction.computeAndWait(() -> ModuleManager.getInstance(alienProject).newModule(alienModuleFile, ModuleType.EMPTY.getId()));
    IndexingTestUtil.waitUntilIndexesAreReady(alienProject);
    VirtualFile alienRoot = HeavyTestHelper.createTestProjectStructure(alienModule, null, structureDirFixture.get(), true);
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(alienProject).commitAllDocuments());
    VirtualFile alienSrc = VfsTestUtil.createDir(alienRoot, "src");
    PsiTestUtil.addSourceContentToRoots(alienModule, alienSrc);
    PsiDirectory psiAlienSrc = ReadAction.computeBlocking(() -> PsiManager.getInstance(alienProject).findDirectory(alienSrc));
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    assertEquals(alienProject, ProjectLocator.getInstance().guessProjectForFile(alienSrc));

    VirtualFile x = VfsTestUtil.createFile(HeavyTestHelper.getOrCreateProjectBaseDir(project), "X.java",
                                           "import java.util.ArrayList; class X { ArrayList<String> a; }");
    PsiFile psiX = ReadAction.computeBlocking(() -> PsiManager.getInstance(project).findFile(x));

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiCopyPasteManager.getInstance().setElements(new PsiElement[]{psiX}, false);
      DataContext context = SimpleDataContext.getSimpleContext(LangDataKeys.PASTE_TARGET_PSI_ELEMENT, psiAlienSrc);
      ((CopyPasteDelegator.MyEditable)new CopyPasteDelegator(alienProject, new JLabel()).getPasteProvider()).performDefaultPaste(context);
    });
  }
}
