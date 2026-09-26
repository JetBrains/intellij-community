// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class FilenameIndexTest extends JavaCodeInsightFixtureTestCase {
  public void testCaseInsensitiveFilesByName() {
    final VirtualFile vFile1 = myFixture.addFileToProject("dir1/foo.test", "Foo").getVirtualFile();
    final VirtualFile vFile2 = myFixture.addFileToProject("dir2/FOO.TEST", "Foo").getVirtualFile();

    GlobalSearchScope scope = GlobalSearchScope.projectScope(getProject());
    assertSameElements(FilenameIndex.getVirtualFilesByName(getProject(), "foo.test", true, scope), vFile1);
    assertSameElements(FilenameIndex.getVirtualFilesByName(getProject(), "FOO.TEST", true, scope), vFile2);

    assertSameElements(FilenameIndex.getVirtualFilesByName(getProject(), "foo.test", false, scope), vFile1, vFile2);
    assertSameElements(FilenameIndex.getVirtualFilesByName(getProject(), "FOO.TEST", false, scope), vFile1, vFile2);
  }

  public void test_getAllFilenames_IsCancellable() {
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    assertThrows(
      ProcessCanceledException.class,
      () -> {
        ProgressManager.getInstance().runProcess(
          () -> {
            progressIndicator.cancel();
            FilenameIndex.getAllFilenames(getProject());
          },
          progressIndicator
        );
      });
  }

  public void test_getVirtualFilesByName_IsCancellable() {
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    assertThrows(
      ProcessCanceledException.class,
      () -> {
        ProgressManager.getInstance().runProcess(
          () -> {
            progressIndicator.cancel();
            FilenameIndex.getVirtualFilesByName("a.java", GlobalSearchScope.everythingScope(getProject()));
          },
          progressIndicator
        );
      });
  }
}
