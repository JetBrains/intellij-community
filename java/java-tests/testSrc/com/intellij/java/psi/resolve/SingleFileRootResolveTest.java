// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public final class SingleFileRootResolveTest {
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final @NotNull LightProjectDescriptor MY_DESCRIPTOR = new LightProjectDescriptor() {
    @Override
    protected void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      try {
        VirtualFile root = contentEntry.getFile().getParent();
        VirtualFile oldRoot = root.findChild("singleFile");
        if (oldRoot != null) {
          oldRoot.delete(this);
        }
        VirtualFile sfRoot = root.createChildDirectory(this, "singleFile");
        VirtualFile aFile = sfRoot.createChildData(this, "A.java");
        aFile.setBinaryContent("package com.example;\npublic class A {C c;}\n".getBytes(StandardCharsets.UTF_8));
        // bFile is not registered as a root
        VirtualFile bFile = sfRoot.createChildData(this, "B.java");
        bFile.setBinaryContent("package com.example;\npublic class B {}\n".getBytes(StandardCharsets.UTF_8));
        // cFile is a package-private class
        VirtualFile cFile = sfRoot.createChildData(this, "C.java");
        cFile.setBinaryContent("package com.example;\nclass C {}\n".getBytes(StandardCharsets.UTF_8));
        ContentEntry newEntry = model.addContentEntry(sfRoot);
        newEntry.addSourceFolder(aFile, false).setPackagePrefix("com.example");
        newEntry.addSourceFolder(cFile, false).setPackagePrefix("com.example");
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(MY_DESCRIPTOR);

  @Test
  public void testSingleFileRootResolve() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Use.java", """
      import com.example.*;
      
      class Use extends A {
        void test() {
          A a = new A();
          <error descr="Cannot resolve symbol 'B'">B</error> b = new <error descr="Cannot resolve symbol 'B'">B</error>();
        }
      }
      """);
    myFixture.checkHighlighting();
    ReadAction.runBlocking(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("com.example.A", GlobalSearchScope.projectScope(myFixture.getProject()));
      assertNotNull(aClass);
      // Works for single-file roots as well
      String pkg = PackageIndex.getInstance(myFixture.getProject()).getPackageName(aClass.getContainingFile().getVirtualFile());
      assertEquals("com.example", pkg);
    });
  }
  
  @Test
  public void testPackagePrivateScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("com.example.A", GlobalSearchScope.projectScope(myFixture.getProject()));
      assertNotNull(aClass);
      PsiClass cClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("com.example.C", GlobalSearchScope.projectScope(myFixture.getProject()));
      assertNotNull(cClass);
      SearchScope scope = cClass.getUseScope();
      assertTrue(scope.contains(aClass.getContainingFile().getVirtualFile()));
      Collection<PsiReference> uses = ReferencesSearch.search(cClass).findAll();
      assertEquals(1, uses.size());
      PsiElement element = uses.iterator().next().getElement();
      PsiJavaCodeReferenceElement ref = assertInstanceOf(PsiJavaCodeReferenceElement.class, element);
      PsiTypeElement typeElement = assertInstanceOf(PsiTypeElement.class, ref.getParent());
      assertInstanceOf(PsiField.class, typeElement.getParent());
    });
  }
  
  @Test
  public void testFindResource() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile cFile = ResourceFileUtil.findResourceFileInProject(myFixture.getProject(), "com/example/C.java");
    assertNotNull(cFile);
    assertEquals("package com.example;\nclass C {}\n", new String(cFile.contentsToByteArray(), StandardCharsets.UTF_8));
    VirtualFile bFile = ResourceFileUtil.findResourceFileInProject(myFixture.getProject(), "com/example/B.java");
    assertNull(bFile);
  }
  
  @Test
  public void testGetFiles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiPackage psiPackage = JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("com.example");
      assertNotNull(psiPackage);
      List<String> fileNames = Stream.of(psiPackage.getFiles(GlobalSearchScope.projectScope(myFixture.getProject())))
        .map(f -> f.getName()).sorted().toList();
      assertEquals(List.of("A.java", "C.java"), fileNames);
    });
  }
}
