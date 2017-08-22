/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ResolveTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.easymock.IArgumentMatcher;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import static org.easymock.EasyMock.*;

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
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.emptyList(), Collections.singletonList(file.getParent().getUrl()));

    assertInstanceOf(ref.resolve(), PsiClass.class);
  }

  public void testModuleSourceAsLibraryClasses() throws Exception {
    final PsiReference ref = configure();
    PsiFile psiFile = ref.getElement().getContainingFile();
    final VirtualFile file = psiFile.getVirtualFile();
    assertNotNull(file);
    createFile(myModule, file.getParent(), "ModuleSourceAsLibraryClassesDep.java", loadFile("class/ModuleSourceAsLibraryClassesDep.java"));
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.singletonList(file.getParent().getUrl()), Collections.emptyList());
    //need this to ensure that PsiJavaFileBaseImpl.myResolveCache is filled to reproduce IDEA-91309
    DependenciesBuilder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
      @Override
      public void process(PsiElement place, PsiElement dependency) {
      }
    });
    assertInstanceOf(ref.resolve(), PsiClass.class);
  }

  public void testStaticImportInTheSameClassPerformance() throws Exception {
    warmUpResolve();

    PsiReference ref = configure();
    ensureIndexUpToDate();
    PlatformTestUtil.startPerformanceTest(getTestName(false), 50, () -> assertNull(ref.resolve()))
      .attempts(1).assertTiming();
  }

  private void ensureIndexUpToDate() {
    getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_LIST, GlobalSearchScope.allScope(myProject));
  }

  private void warmUpResolve() {
    PsiJavaCodeReferenceElement ref = JavaPsiFacade.getElementFactory(myProject).createReferenceFromText("java.util.List<String>", null);
    JavaResolveResult result = ref.advancedResolve(false);
    assertNotNull(result.getElement());
    assertSize(1, result.getSubstitutor().getSubstitutionMap().keySet());
  }

  public void testStaticImportNetworkPerformance() throws Exception {
    warmUpResolve();

    PsiReference ref = configure();
    int count = 15;

    String imports = "";
    for (int i = 0; i < count; i++) {
      imports += "import static Foo" + i + ".*;\n";
    }

    for (int i = 0; i < count; i++) {
      createFile(myModule, "Foo" + i + ".java", imports + "class Foo" + i + " extends Bar1, Bar2, Bar3 {}");
    }

    ensureIndexUpToDate();
    PlatformTestUtil.startPerformanceTest(getTestName(false), 800, () -> assertNull(ref.resolve()))
      .attempts(1).assertTiming();
  }

  public void testQualifiedAnonymousClass() throws Exception {
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());

    PsiReference ref = configure();
    VirtualFile file = ref.getElement().getContainingFile().getVirtualFile();
    assertNotNull(file);
    VirtualFile pkg = WriteAction.compute(() -> file.getParent().createChildDirectory(this, "foo"));
    createFile(myModule, pkg, "Outer.java", "package foo; public class Outer { protected static class Inner { protected Inner() {} } }");

    assertEquals("Inner", assertInstanceOf(ref.resolve(), PsiClass.class).getName());
  }

  @SuppressWarnings({"ConstantConditions"})
  private void configureDependency() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      Module module = modifiableModel.newModule("a.iml", StdModuleTypes.JAVA.getId());
      modifiableModel.commit();

      VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestDataPath() + "/class/dependentModule");
      assert root != null;

      PsiTestUtil.addContentRoot(module, root);
      PsiTestUtil.addSourceRoot(module, root.findChild("src"));
      PsiTestUtil.addSourceRoot(module, root.findChild("test"), true);

      ModuleRootModificationUtil.addDependency(getModule(), module);
    });
  }

  private PsiReference configure() throws Exception {
    return configureByFile("class/" + getTestName(false) + ".java");
  }

  public void testNoSubpackagesAccess() throws Exception {
    PsiElementFinder mock = createMockFinder();
    PlatformTestUtil.registerExtension(Extensions.getArea(getProject()), PsiElementFinder.EP_NAME, mock, getTestRootDisposable());

    PsiReference reference = configure();
    assertNull(reference.resolve());
    reference.getVariants();

    verify(mock);
  }

  private static PsiElementFinder createMockFinder() {
    Set<String> ignoredMethods = ContainerUtil.newHashSet("getClassesFilter", "processPackageDirectories", "getClasses");
    Method[] methods = ContainerUtil.findAllAsArray(PsiElementFinder.class.getDeclaredMethods(), m -> !ignoredMethods.contains(m.getName()));
    PsiElementFinder mock = createMockBuilder(PsiElementFinder.class).addMockedMethods(methods).createMock();
    expect(mock.findClasses(anyObject(), anyObject())).andReturn(PsiClass.EMPTY_ARRAY).anyTimes();
    expect(mock.findPackage(eq("foo"))).andReturn(null);
    expect(mock.getSubPackages(rootPackage(), anyObject())).andReturn(PsiPackage.EMPTY_ARRAY);
    replay(mock);
    return mock;
  }

  private static PsiPackage rootPackage() {
    reportMatcher(new IArgumentMatcher() {
      @Override
      public boolean matches(Object argument) {
        return "PsiPackage:".equals(String.valueOf(argument));
      }

      @Override
      public void appendTo(StringBuffer buffer) {
        buffer.append("PsiPackage:");
      }
    });
    return null;
  }
}
