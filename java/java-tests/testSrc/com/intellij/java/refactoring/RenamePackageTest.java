// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.rename.RenamePsiPackageProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RenamePackageTest {
  protected final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(PathManager.getHomePath(), true, builder -> builder.setLanguageLevel(LanguageLevel.JDK_25));

  @Test
  public void testOrderingWithInjected() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile one = myFixture.addFileToProject("src/com/a/Amorphus.java", """
      package com.a;
      public class Amorphus {
        void x() {
          //language=JAVA
          String java = ""\"
            import com.a.Amorphus;
            class A {
              void x() {
                  new Amorphus();
                  new com.a.Amorphus();
              }
            }
            ""\";
        }
      }
      """).getVirtualFile();
    VirtualFile two = myFixture.addFileToProject("test/com/a/Iconoclass.java", """
      package com.a;
      public class Iconoclass {
        void main() {
          new Amorphus();
      }""").getVirtualFile();
    PsiTestUtil.removeSourceRoot(myFixture.getModule(), ModuleRootManager.getInstance(myFixture.getModule()).getSourceRoots()[0]);
    PsiTestUtil.addSourceRoot(myFixture.getModule(), myFixture.getTempDirFixture().getFile("src"));
    PsiTestUtil.addSourceRoot(myFixture.getModule(), myFixture.getTempDirFixture().getFile("test"), true);
    PsiPackage p = myFixture.findPackage("com.a");
    myFixture.testHighlighting("src/com/a/Amorphus.java");
    EdtTestUtil.runInEdtAndWait(() -> {
      RenamePsiPackageProcessor.createRenameMoveProcessor("x.y.z", p, false, false).run();
      FileDocumentManager.getInstance().saveAllDocuments();
    });

    assertTrue(one.getPath().endsWith("src/x/y/z/Amorphus.java"));
    assertTrue(two.getPath().endsWith("test/x/y/z/Iconoclass.java"));

    assertEquals("""
                   package x.y.z;
                   public class Amorphus {
                     void x() {
                       //language=JAVA
                       String java = ""\"
                         import x.y.z.Amorphus;
                         class A {
                           void x() {
                               new Amorphus();
                               new Amorphus();
                           }
                         }
                         ""\";
                     }
                   }
                   """, VfsUtilCore.loadText(one));
    assertEquals("""
                   package x.y.z;
                   public class Iconoclass {
                     void main() {
                       new Amorphus();
                   }""", VfsUtilCore.loadText(two));

    ReadAction.runBlocking(() -> {
      assertNull(myFixture.getJavaFacade().findPackage("p"));
      assertEquals(2, myFixture.getJavaFacade().findPackage("x.y.z").getDirectories().length);
    });
  }
  
  @Test
  public void testRenameInPackagePrefix() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile a = myFixture.addFileToProject("srcPrefix/foo/A.java", "package p.foo; class A extends B {}").getVirtualFile();
    VirtualFile b = myFixture.addFileToProject("src/p/foo/B.java", "package p.foo; class B { }").getVirtualFile();
    VirtualFile c = myFixture.addFileToProject("src/bar/C.java", "package bar; class C { p.foo.A a; p.foo.B b; }").getVirtualFile();

    PsiTestUtil.removeSourceRoot(myFixture.getModule(), ModuleRootManager.getInstance(myFixture.getModule()).getSourceRoots()[0]);
    PsiTestUtil.addSourceRoot(myFixture.getModule(), myFixture.getTempDirFixture().getFile("src"));
    PsiTestUtil.addSourceRoot(myFixture.getModule(), myFixture.getTempDirFixture().getFile("srcPrefix"), JavaSourceRootType.SOURCE,
                              JpsJavaExtensionService.getInstance().createSourceRootProperties("p"));

    EdtTestUtil.runInEdtAndWait(() -> {
      rename(myFixture.findPackage("p"), "p1");
      FileDocumentManager.getInstance().saveAllDocuments();
    });

    assertTrue(a.getPath().endsWith("srcPrefix/foo/A.java"));
    assertTrue(b.getPath().endsWith("src/p1/foo/B.java"));
    assertTrue(c.getPath().endsWith("src/bar/C.java"));

    assertEquals("package p1.foo; class A extends B {}", VfsUtilCore.loadText(a));
    assertEquals("package p1.foo; class B { }", VfsUtilCore.loadText(b));
    assertEquals("package bar; class C { p1.foo.A a; p1.foo.B b; }", VfsUtilCore.loadText(c));

    ReadAction.runBlocking(() -> {
      assertNull(myFixture.getJavaFacade().findPackage("p"));
      assertEquals(2, myFixture.getJavaFacade().findPackage("p1").getDirectories().length);
    });
  }

  @Test
  public void testRenameInResources() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("src/p/p/m.txt", "").getVirtualFile();
    VirtualFile c = myFixture.addFileToProject("src/bar/C.java", """
      package bar;\s
      class C {\s
        {
           C.class.getResource("/p/p/m.txt");
        }\s
      }""").getVirtualFile();

    PsiTestUtil.removeSourceRoot(myFixture.getModule(), ModuleRootManager.getInstance(myFixture.getModule()).getSourceRoots()[0]);
    PsiTestUtil.addSourceRoot(myFixture.getModule(), myFixture.getTempDirFixture().getFile("src"));

    EdtTestUtil.runInEdtAndWait(() -> {
      rename(myFixture.findPackage("p.p"), "p.p1");
      FileDocumentManager.getInstance().saveAllDocuments();
    });

    assertEquals("""
                   package bar;\s
                   class C {\s
                     {
                        C.class.getResource("/p/p1/m.txt");
                     }\s
                   }""", VfsUtilCore.loadText(c));
  }

  /** Renames {@code element} as the fixture does; a subclass renames with another engine. */
  protected void rename(@NotNull PsiElement element, @NotNull String newName) {
    codeInsightFixture.get().renameElement(element, newName);
  }
}
