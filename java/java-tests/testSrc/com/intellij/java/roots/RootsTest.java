// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RootsTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testTest1() {
    Module myModule = heavyFixture.get().getModule();
    final String rootPath = JavaTestUtil.getJavaTestDataPath() + "/moduleRootManager/roots/" + "test1";
    final VirtualFile rootFile = WriteAction.computeAndWait(
      () -> StandardFileSystems.local().refreshAndFindFileByPath(rootPath.replace(File.separatorChar, '/')));
    final VirtualFile classesFile = rootFile.findChild("classes");
    assertNotNull(classesFile);
    final VirtualFile childOfContent = rootFile.findChild("x.txt");
    assertNotNull(childOfContent);
    final VirtualFile childOfClasses = classesFile.findChild("y.txt");
    assertNotNull(childOfClasses);

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);


    PsiTestUtil.addContentRoot(myModule, rootFile);
    PsiTestUtil.setCompilerOutputPath(myModule, classesFile.getUrl(), false);
    PsiTestUtil.setExcludeCompileOutput(myModule, false);
    ReadAction.runBlocking(() -> {
      assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
      assertTrue(rootManager.getFileIndex().isInContent(childOfClasses));
    });

    PsiTestUtil.setExcludeCompileOutput(myModule, true);
    ReadAction.runBlocking(() -> {
      assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
      assertFalse(rootManager.getFileIndex().isInContent(childOfClasses));
    });
  }

}
