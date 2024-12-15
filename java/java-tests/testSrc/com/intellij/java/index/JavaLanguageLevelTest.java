// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.java.stubs.JavaStubDefinition;
import com.intellij.psi.stubs.SerializedStubTree;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;

public class JavaLanguageLevelTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSourceRootAddedForExistingFiles() {
    VirtualFile file = myFixture.addFileToProject("../src2/A.java", "class A {}").getVirtualFile();
    LanguageLevel level = JavaLanguageLevelPusher.getPushedLanguageLevel(file);

    assertFalse(ProjectFileIndex.getInstance(getProject()).isInSourceContent(file));
    assertNull(level);
    assertFalse(JavaStubDefinition.shouldBuildStubForFile(file));
    assertNull(getIndexedStub(file));

    PsiTestUtil.addSourceContentToRoots(myFixture.getModule(), file.getParent());
    try {
      LanguageLevel level2 = JavaLanguageLevelPusher.getPushedLanguageLevel(file);

      assertTrue(ProjectFileIndex.getInstance(getProject()).isInSourceContent(file));
      assertEquals(LanguageLevel.HIGHEST, level2);
      assertTrue(JavaStubDefinition.shouldBuildStubForFile(file));
      assertNotNull(getIndexedStub(file));
    }
    finally {
      PsiTestUtil.removeContentEntry(myFixture.getModule(), file.getParent());
    }
  }

  private SerializedStubTree getIndexedStub(VirtualFile file) {
    return ContainerUtil.getFirstItem(FileBasedIndex.getInstance().getFileData(StubUpdatingIndex.INDEX_ID, file, getProject()).values());
  }
}
