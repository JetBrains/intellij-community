// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildData;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.setFileText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class SameSourceRootInTwoModulesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile myPrjDir1;
  private VirtualFile mySrcDir1;
  private VirtualFile myPackDir;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module myModule = fixture.getModule();
    VirtualFile rootVFile = fixture.createTempVirtualDir();
    WriteAction.runAndWait(() -> {
      myPrjDir1 = createChildDirectory(rootVFile, "prj1");
      mySrcDir1 = createChildDirectory(myPrjDir1, "src1");

      myPackDir = createChildDirectory(mySrcDir1, "p");
      VirtualFile file1 = createChildData(myPackDir, "A.java");
      setFileText(file1, "package p; public class A{ public void foo(); }");
      PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();

      PsiTestUtil.addContentRoot(myModule, myPrjDir1);
      PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
    });
  }

  @Test
  public void testBug() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project myProject = fixture.getProject();
    Module myModule = fixture.getModule();
    WriteAction.runAndWait(() -> {
      PsiClass psiClass = fixture.getJavaFacade().findClass("p.A");
      assertEquals("p.A", psiClass.getQualifiedName());

      PsiFile psiFile = fixture.getPsiManager().findFile(myPackDir.findChild("A.java"));
      //noinspection ResultOfMethodCallIgnored -- load PSI
      psiFile.getChildren();
      assertEquals(psiFile, psiClass.getContainingFile());

      VirtualFile file = psiFile.getVirtualFile();
      assertEquals(myModule, ModuleUtilCore.findModuleForFile(file, myProject));

      Module anotherModule = fixture.createModule("another");

      PsiTestUtil.addSourceRoot(anotherModule, mySrcDir1);

      assertNotNull(ModuleUtilCore.findModuleForFile(file, myProject));
    });
  }
}
