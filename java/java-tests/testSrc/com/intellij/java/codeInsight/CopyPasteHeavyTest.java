// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TemporaryDirectory;

import javax.swing.JLabel;
import java.nio.file.Path;

public class CopyPasteHeavyTest extends HeavyPlatformTestCase {
  public void testMoveFileFromTheOtherProjectDoesntCrash() {
    Path tempPath = TemporaryDirectory.generateTemporaryPath("second");
    Project alienProject = PlatformTestUtil.loadAndOpenProject(tempPath.resolve("alienProj.ipr"), getTestRootDisposable());
    Module alienModule = doCreateRealModuleIn("x", alienProject, getModuleType());
    VirtualFile alienRoot = createTestProjectStructure(alienProject, alienModule, null, true);
    VirtualFile alienSrc = createChildDirectory(alienRoot, "src");
    PsiTestUtil.addSourceContentToRoots(alienModule, alienSrc);
    PsiDirectory psiAlienSrc = PsiManager.getInstance(alienProject).findDirectory(alienSrc);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    assertEquals(alienProject, ProjectLocator.getInstance().guessProjectForFile(alienSrc));

    VirtualFile x = createChildData(getOrCreateProjectBaseDir(), "X.java");
    setFileText(x, "import java.util.ArrayList; class X { ArrayList<String> a; }");
    PsiFile psiX = getPsiManager().findFile(x);

    PsiCopyPasteManager.getInstance().setElements(new PsiElement[]{psiX}, false);
    DataContext context = SimpleDataContext.getSimpleContext(LangDataKeys.PASTE_TARGET_PSI_ELEMENT, psiAlienSrc);
    ((CopyPasteDelegator.MyEditable)new CopyPasteDelegator(alienProject, new JLabel()).getPasteProvider()).performDefaultPaste(context);
  }
}
