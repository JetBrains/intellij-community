// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An environment which represents one file by more than one {@link VirtualFile} instance, such as the analyzer of the language
 * server, asks the scope with an instance which the scope did not collect, so the scope has to answer by the URL of the file.
 */
public class PackageScopeTest extends LightJavaCodeInsightFixtureTestCase {

  public void testDirectoryOfThePackage() {
    VirtualFile file = addFile("pkg/sub/Helper.java", """
      package pkg.sub;

      class Helper { }
      """);

    assertContains("pkg.sub", false, file);
    assertContains("pkg.sub", true, file);
    assertContains("pkg", true, file);
    assertNotContains("pkg", false, file);
    assertNotContains("", false, file);
    assertContains("", true, file);
  }

  public void testDeclaredPackageWithoutADirectory() {
    VirtualFile file = addFile("Helper.java", """
      package org.classa.sub;

      class Helper { }
      """);

    assertContains("org.classa.sub", false, file);
    assertContains("org.classa.sub", true, file);
    assertContains("org.classa", true, file);
    assertNotContains("org.classa", false, file);
    assertContains("org", true, file);
  }

  public void testFileOfAnotherPackageIsNotContained() {
    VirtualFile inDirectory = addFile("pkg/sub/Helper.java", """
      package pkg.sub;

      class Helper { }
      """);
    VirtualFile declared = addFile("Other.java", """
      package org.classa;

      class Other { }
      """);

    assertNotContains("pkg", true, declared);
    assertNotContains("pkg.sub", true, declared);
    assertNotContains("org.classa", true, inDirectory);
  }

  private @NotNull VirtualFile addFile(@NotNull String path, @NotNull String text) {
    PsiFile file = myFixture.addFileToProject(path, text);
    VirtualFile virtualFile = file.getVirtualFile();
    assertNotNull(virtualFile);
    return virtualFile;
  }

  private void assertContains(@NotNull String packageName, boolean includeSubpackages, @NotNull VirtualFile file) {
    assertTrue(packageName + ", includeSubpackages = " + includeSubpackages, contains(packageName, includeSubpackages, file));
  }

  private void assertNotContains(@NotNull String packageName, boolean includeSubpackages, @NotNull VirtualFile file) {
    assertFalse(packageName + ", includeSubpackages = " + includeSubpackages, contains(packageName, includeSubpackages, file));
  }

  /**
   * Asks the scope both with the instance of the file itself and with a duplicate of it, and asserts that both answers agree.
   */
  private boolean contains(@NotNull String packageName, boolean includeSubpackages, @NotNull VirtualFile file) {
    PsiPackage psiPackage = JavaPsiFacade.getInstance(getProject()).findPackage(packageName);
    assertNotNull(packageName, psiPackage);
    GlobalSearchScope scope = PackageScope.packageScope(psiPackage, includeSubpackages);
    boolean contains = scope.contains(file);
    assertEquals("the scope must not depend on the instance which represents the file",
                 contains, scope.contains(new DuplicateVirtualFile(file)));
    return contains;
  }

  /**
   * A second {@link VirtualFile} instance for the same URL. It is neither equal to nor a {@code VirtualFileWithId} of the original,
   * so only the URL relates the two, exactly as in the analyzer of the language server.
   */
  private static final class DuplicateVirtualFile extends VirtualFile {
    private final VirtualFile myOriginal;

    private DuplicateVirtualFile(@NotNull VirtualFile original) {
      myOriginal = original;
    }

    @Override
    public @NotNull String getName() {
      return myOriginal.getName();
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
      return myOriginal.getFileSystem();
    }

    @Override
    public @NotNull String getPath() {
      return myOriginal.getPath();
    }

    @Override
    public boolean isWritable() {
      return myOriginal.isWritable();
    }

    @Override
    public boolean isDirectory() {
      return myOriginal.isDirectory();
    }

    @Override
    public boolean isValid() {
      return myOriginal.isValid();
    }

    @Override
    public VirtualFile getParent() {
      VirtualFile parent = myOriginal.getParent();
      return parent == null ? null : new DuplicateVirtualFile(parent);
    }

    @Override
    public VirtualFile[] getChildren() {
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
      return myOriginal.getOutputStream(requestor, newModificationStamp, newTimeStamp);
    }

    @Override
    public byte @NotNull [] contentsToByteArray() throws IOException {
      return myOriginal.contentsToByteArray();
    }

    @Override
    public long getTimeStamp() {
      return myOriginal.getTimeStamp();
    }

    @Override
    public long getLength() {
      return myOriginal.getLength();
    }

    @Override
    public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
      myOriginal.refresh(asynchronous, recursive, postRunnable);
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
      return myOriginal.getInputStream();
    }
  }
}
