// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildData;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.setFileText;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class SourceRootAddedAsLibraryRootTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile myDir;
  private VirtualFile myVFile;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile rootVFile = fixture.createTempVirtualDir();

    myDir = createChildDirectory(rootVFile, "contentAndLibrary");

    PsiTestUtil.addSourceRoot(fixture.getModule(), myDir);
  }

  private void changeRoots() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    ModuleRootModificationUtil.addModuleLibrary(fixture.getModule(), myDir.getUrl());
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
  }

  @Test
  public void testBug() {
    touchFileSync();
    ReadAction.runBlocking(() -> {
      PsiFile psiFile = heavyFixture.get().getPsiManager().findFile(myVFile);
      assertNotNull(psiFile.getText());
    });
    changeRoots();
  }

  private void touchFileSync() {
    myVFile = createChildData(myDir, "A.java");
    setFileText(myVFile, "package p; public class A{ public void foo(); }");
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(heavyFixture.get().getProject()).commitAllDocuments());
  }
}
