// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public final class SingleFileRootResolveTest extends LightJavaCodeInsightFixtureTestCase {
  private final @NotNull LightProjectDescriptor MY_DESCRIPTOR = new LightProjectDescriptor() {
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

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return MY_DESCRIPTOR;
  }

  public void testSingleFileRootResolve() {
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
    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("com.example.A", GlobalSearchScope.projectScope(getProject()));
    assertNotNull(aClass);
    // Works for single-file roots as well
    String pkg = PackageIndex.getInstance(getProject()).getPackageNameByDirectory(aClass.getContainingFile().getVirtualFile());
    assertEquals("com.example", pkg);
  }
  
  public void testPackagePrivateScope() {
    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("com.example.A", GlobalSearchScope.projectScope(getProject()));
    assertNotNull(aClass);
    PsiClass cClass = JavaPsiFacade.getInstance(getProject()).findClass("com.example.C", GlobalSearchScope.projectScope(getProject()));
    assertNotNull(cClass);
    SearchScope scope = cClass.getUseScope();
    assertTrue(scope.contains(aClass.getContainingFile().getVirtualFile()));
    Collection<PsiReference> uses = ReferencesSearch.search(cClass).findAll();
    assertEquals(1, uses.size());
    PsiElement element = uses.iterator().next().getElement();
    PsiJavaCodeReferenceElement ref = assertInstanceOf(element, PsiJavaCodeReferenceElement.class);
    PsiTypeElement typeElement = assertInstanceOf(ref.getParent(), PsiTypeElement.class);
    assertInstanceOf(typeElement.getParent(), PsiField.class);
  }
}
