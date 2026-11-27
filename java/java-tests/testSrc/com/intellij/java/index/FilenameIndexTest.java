// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestApplication
public class FilenameIndexTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testCaseInsensitiveFilesByName() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final VirtualFile vFile1 = myFixture.addFileToProject("dir1/foo.test", "Foo").getVirtualFile();
    final VirtualFile vFile2 = myFixture.addFileToProject("dir2/FOO.TEST", "Foo").getVirtualFile();

    GlobalSearchScope scope = GlobalSearchScope.projectScope(myFixture.getProject());
    ReadAction.runBlocking(() -> assertThat(FilenameIndex.getVirtualFilesByName(myFixture.getProject(), "foo.test", true, scope)).containsExactlyInAnyOrder(vFile1));
    ReadAction.runBlocking(() -> assertThat(FilenameIndex.getVirtualFilesByName(myFixture.getProject(), "FOO.TEST", true, scope)).containsExactlyInAnyOrder(vFile2));

    ReadAction.runBlocking(() -> assertThat(FilenameIndex.getVirtualFilesByName(myFixture.getProject(), "foo.test", false, scope)).containsExactlyInAnyOrder(vFile1, vFile2));
    ReadAction.runBlocking(() -> assertThat(FilenameIndex.getVirtualFilesByName(myFixture.getProject(), "FOO.TEST", false, scope)).containsExactlyInAnyOrder(vFile1, vFile2));
  }

  @Test
  public void test_getAllFilenames_IsCancellable() {
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    assertThrows(
      ProcessCanceledException.class,
      () -> {
        ProgressManager.getInstance().runProcess(
          () -> {
            progressIndicator.cancel();
            ReadAction.runBlocking(() -> FilenameIndex.getAllFilenames(codeInsightFixture.get().getProject()));
          },
          progressIndicator
        );
      });
  }

  @Test
  public void test_getVirtualFilesByName_IsCancellable() {
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    assertThrows(
      ProcessCanceledException.class,
      () -> {
        ProgressManager.getInstance().runProcess(
          () -> {
            progressIndicator.cancel();
            ReadAction.runBlocking(() -> FilenameIndex.getVirtualFilesByName("a.java", GlobalSearchScope.everythingScope(codeInsightFixture.get().getProject())));
          },
          progressIndicator
        );
      });
  }
}
