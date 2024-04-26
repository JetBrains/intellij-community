// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

import java.io.IOException;

public class RenamePackageTest extends JavaCodeInsightFixtureTestCase {
  public void testRenameInPackagePrefix() throws IOException {
    VirtualFile a = myFixture.addFileToProject("srcPrefix/foo/A.java", "package p.foo; class A extends B {}").getVirtualFile();
    VirtualFile b = myFixture.addFileToProject("src/p/foo/B.java", "package p.foo; class B { }").getVirtualFile();
    VirtualFile c = myFixture.addFileToProject("src/bar/C.java", "package bar; class C { p.foo.A a; p.foo.B b; }").getVirtualFile();

    PsiTestUtil.removeSourceRoot(getModule(), ModuleRootManager.getInstance(getModule()).getSourceRoots()[0]);
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().getFile("src"));
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().getFile("srcPrefix"), JavaSourceRootType.SOURCE,
                              JpsJavaExtensionService.getInstance().createSourceRootProperties("p"));

    myFixture.renameElement(myFixture.findPackage("p"), "p1");
    FileDocumentManager.getInstance().saveAllDocuments();

    assertTrue(a.getPath().endsWith("srcPrefix/foo/A.java"));
    assertTrue(b.getPath().endsWith("src/p1/foo/B.java"));
    assertTrue(c.getPath().endsWith("src/bar/C.java"));

    assertEquals("package p1.foo; class A extends B {}", VfsUtilCore.loadText(a));
    assertEquals("package p1.foo; class B { }", VfsUtilCore.loadText(b));
    assertEquals("package bar; class C { p1.foo.A a; p1.foo.B b; }", VfsUtilCore.loadText(c));

    assertNull(myFixture.getJavaFacade().findPackage("p"));
    assertEquals(2, myFixture.getJavaFacade().findPackage("p1").getDirectories().length);
  }

  public void testRenameInResources() throws IOException {
    myFixture.addFileToProject("src/p/p/m.txt", "").getVirtualFile();
    VirtualFile c = myFixture.addFileToProject("src/bar/C.java", """
      package bar;\s
      class C {\s
        {
           C.class.getResource("/p/p/m.txt");
        }\s
      }""").getVirtualFile();

    PsiTestUtil.removeSourceRoot(getModule(), ModuleRootManager.getInstance(getModule()).getSourceRoots()[0]);
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().getFile("src"));

    myFixture.renameElement(myFixture.findPackage("p.p"), "p.p1");
    FileDocumentManager.getInstance().saveAllDocuments();

    assertEquals("""
                   package bar;\s
                   class C {\s
                     {
                        C.class.getResource("/p/p1/m.txt");
                     }\s
                   }""", VfsUtilCore.loadText(c));
  }
}
