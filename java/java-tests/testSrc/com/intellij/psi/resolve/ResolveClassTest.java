/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ResolveTestCase;

import java.util.Collections;

public class ResolveClassTest extends ResolveTestCase {
  public void testFQName() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testVarInNew() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testVarInNew1() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testPrivateInExtends() throws Exception {
    PsiReference ref = configure();
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertTrue(target instanceof PsiClass);
    assertFalse(result.isAccessible());
  }

  public void testQNew1() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testInnerPrivateMember1() throws Exception {
    PsiReference ref = configure();
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertTrue(target instanceof PsiClass);
    assertTrue(result.isValidResult());
  }


  public void testQNew2() throws Exception {
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configure();
    PsiElement target = ref.advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);

    PsiElement parent = ref.getParent();
    assertTrue(parent instanceof PsiAnonymousClass);
    ((PsiAnonymousClass)parent).getBaseClassType().resolve();

    assertEquals(target, ((PsiAnonymousClass)parent).getBaseClassType().resolve());
  }

  public void testClassExtendsItsInner1() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    assertEquals("B.Foo", ((PsiClass)target).getQualifiedName());

    PsiReference refCopy = ref.getElement().copy().getReference();
    assert refCopy != null;
    PsiElement target1 = ((PsiJavaReference)refCopy).advancedResolve(true).getElement();
    assertTrue(target1 instanceof PsiClass);
    //assertNull(target1.getContainingFile().getVirtualFile());
    assertEquals("B.Foo", ((PsiClass)target1).getQualifiedName());
  }

  public void testClassExtendsItsInner2() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);  //[ven] this should not be resolved
    /*assertTrue(target instanceof PsiClass);
    assertEquals("TTT.Bar", ((PsiClass)target).getQualifiedName());*/
  }

  public void testSCR40332() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);
  }

  public void testImportConflict1() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target == null);
  }

  public void testImportConflict2() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    assertEquals("java.util.Date", ((PsiClass)target).getQualifiedName());
  }

  public void testLocals1() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    // local class
    assertNull(((PsiClass)target).getQualifiedName());
  }

  public void testLocals2() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    // local class
    assertNull(((PsiClass)target).getQualifiedName());
  }

  public void testShadowing() throws Exception {
    PsiReference ref = configure();
    JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    assertTrue(result.getElement() instanceof PsiClass);
    assertTrue(!result.isValidResult());
    assertTrue(!result.isAccessible());
  }

  public void testStaticImportVsImplicit() throws Exception {
    PsiReference ref = configure();
    JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    final PsiElement element = result.getElement();
    assertTrue(element instanceof PsiClass);
    assertTrue("Outer.Double".equals(((PsiClass)element).getQualifiedName()));
  }

  public void testTwoModules() throws Exception {
    configureDependency();
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(String.valueOf(target), target instanceof PsiClass);
  }

  public void testTwoModules2() throws Exception {
    configureDependency();
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);
  }

  public void testModuleSourceAsLibrarySource() throws Exception {
    final PsiReference ref = configure();
    final VirtualFile file = ref.getElement().getContainingFile().getVirtualFile();
    assertNotNull(file);
    createFile(myModule, file.getParent(), "ModuleSourceAsLibrarySourceDep.java", loadFile("class/ModuleSourceAsLibrarySourceDep.java"));
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.<String>emptyList(), Collections.singletonList(file.getParent().getUrl()));

    assertInstanceOf(ref.resolve(), PsiClass.class);
  }

  public void testModuleSourceAsLibraryClasses() throws Exception {
    final PsiReference ref = configure();
    PsiFile psiFile = ref.getElement().getContainingFile();
    final VirtualFile file = psiFile.getVirtualFile();
    assertNotNull(file);
    createFile(myModule, file.getParent(), "ModuleSourceAsLibraryClassesDep.java", loadFile("class/ModuleSourceAsLibraryClassesDep.java"));
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.singletonList(file.getParent().getUrl()), Collections.<String>emptyList());
    //need this to ensure that PsiJavaFileBaseImpl.myResolveCache is filled to reproduce IDEA-91309
    DependenciesBuilder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
      @Override
      public void process(PsiElement place, PsiElement dependency) {
      }
    });
    assertInstanceOf(ref.resolve(), PsiClass.class);
  }


  public void testStaticImportInTheSameClass() throws Exception {
    PsiReference ref = configure();
    long start = System.currentTimeMillis();
    assertNull(ref.resolve());
    long elapsed = System.currentTimeMillis() - start;
    PlatformTestUtil.assertTiming("exponent?", 500, elapsed);
  }

  public void testStaticImportNetwork() throws Exception {
    PsiReference ref = configure();
    int count = 15;

    String imports = "";
    for (int i = 0; i < count; i++) {
      imports += "import static Foo" + i + ".*;\n";
    }

    for (int i = 0; i < count; i++) {
      createFile(myModule, "Foo" + i + ".java", imports + "class Foo" + i + " extends Bar1, Bar2, Bar3 {}");
    }

    System.gc();
    long start = System.currentTimeMillis();
    assertNull(ref.resolve());
    PlatformTestUtil.assertTiming("exponent?", 20000, System.currentTimeMillis() - start);
  }

  @SuppressWarnings({"ConstantConditions"})
  private void configureDependency() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
        Module module = modifiableModel.newModule("a.iml", StdModuleTypes.JAVA.getId());
        modifiableModel.commit();

        VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestDataPath() + "/class/dependentModule");
        assert root != null;

        PsiTestUtil.addContentRoot(module, root);
        PsiTestUtil.addSourceRoot(module, root.findChild("src"));
        PsiTestUtil.addSourceRoot(module, root.findChild("test"), true);

        ModuleRootModificationUtil.addDependency(getModule(), module);
      }
    });
  }

  private PsiReference configure() throws Exception {
    return configureByFile("class/" + getTestName(false) + ".java");
  }
}
