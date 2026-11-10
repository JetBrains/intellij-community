// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.ContentRootEntity;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.java.stubs.JavaStubDefinition;
import com.intellij.psi.stubs.SerializedStubTree;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaLanguageLevelTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSourceRootAddedForExistingFiles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.addFileToProject("../src2/A.java", "class A {}").getVirtualFile();
    LanguageLevel level = JavaLanguageLevelPusher.getPushedLanguageLevel(file);

    assertFalse(ProjectFileIndex.getInstance(myFixture.getProject()).isInSourceContent(file));
    assertNull(level);
    assertFalse(JavaStubDefinition.shouldBuildStubForFile(file));
    assertNull(getIndexedStub(file));

    PsiTestUtil.addSourceContentToRoots(myFixture.getModule(), file.getParent());
    try {
      LanguageLevel level2 = JavaLanguageLevelPusher.getPushedLanguageLevel(file);

      assertTrue(ProjectFileIndex.getInstance(myFixture.getProject()).isInSourceContent(file));
      assertEquals(LanguageLevel.HIGHEST, level2);
      assertTrue(JavaStubDefinition.shouldBuildStubForFile(file));
      assertNotNull(getIndexedStub(file));
    }
    finally {
      PsiTestUtil.removeContentEntry(myFixture.getModule(), file.getParent());
    }
  }

  @Test
  public void testSourceRootOverlappedByCustomFileSet() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    WorkspaceFileIndexImpl.EP_NAME.getPoint().registerExtension(new CustomKindContentRootContributor(), myFixture.getTestRootDisposable());
    List<VirtualFile> files = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      files.add(myFixture.addFileToProject("../src3/p" + i + "/q/A" + i + ".java", "package p" + i + ".q; class A" + i + " {}").getVirtualFile());
    }
    VirtualFile root = files.getFirst().getParent().getParent().getParent();

    PsiTestUtil.addSourceContentToRoots(myFixture.getModule(), root);
    try {
      for (VirtualFile file : files) {
        assertEquals(LanguageLevel.HIGHEST, JavaLanguageLevelPusher.getPushedLanguageLevel(file), file.getPath());
        assertTrue(JavaStubDefinition.shouldBuildStubForFile(file), file.getPath());
        assertNotNull(getIndexedStub(file), file.getPath());
      }
    }
    finally {
      PsiTestUtil.removeContentEntry(myFixture.getModule(), root);
    }
  }

  private SerializedStubTree getIndexedStub(VirtualFile file) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> ContainerUtil.getFirstItem(FileBasedIndex.getInstance().getFileData(StubUpdatingIndex.INDEX_ID, file, myFixture.getProject()).values()));
  }

  private static final class CustomKindContentRootContributor implements WorkspaceFileIndexContributor<ContentRootEntity> {
    @Override
    public @NotNull Class<ContentRootEntity> getEntityClass() {
      return ContentRootEntity.class;
    }

    @Override
    public void registerFileSets(@NotNull ContentRootEntity entity,
                                 @NotNull WorkspaceFileSetRegistrar registrar,
                                 @NotNull EntityStorage storage) {
      if (entity.getUrl().getUrl().endsWith("/src3")) {
        registrar.registerFileSet(entity.getUrl(), WorkspaceFileKind.CUSTOM, entity, null);
      }
    }
  }
}
