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
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.util.containers.ContainerUtil;
import org.easymock.IArgumentMatcher;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import static org.easymock.EasyMock.*;

public class ResolveClassInModulesWithDependenciesTest extends ResolveTestCase {
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
    VirtualFile dir = createTempVfsDirectory();
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.emptyList(), Collections.singletonList(dir.getUrl()));

    final PsiReference ref = configureByFile("class/" + getTestName(false) + ".java", dir);
    final VirtualFile file = ref.getElement().getContainingFile().getVirtualFile();
    assertNotNull(file);
    createFile(myModule, file.getParent(), "ModuleSourceAsLibrarySourceDep.java", loadFile("class/ModuleSourceAsLibrarySourceDep.java"));

    assertInstanceOf(ref.resolve(), PsiClass.class);
  }

  public void testModuleSourceAsLibraryClasses() throws Exception {
    VirtualFile dir = createTempVfsDirectory();
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.singletonList(dir.getUrl()), Collections.emptyList());

    PsiReference ref = configureByFile("class/" + getTestName(false) + ".java", dir);
    PsiFile psiFile = ref.getElement().getContainingFile();
    final VirtualFile file = psiFile.getVirtualFile();
    assertNotNull(file);
    createFile(myModule, dir, "ModuleSourceAsLibraryClassesDep.java", loadFile("class/ModuleSourceAsLibraryClassesDep.java"));
    //need this to ensure that PsiJavaFileBaseImpl.myResolveCache is filled to reproduce IDEA-91309
    DependenciesBuilder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
      @Override
      public void process(PsiElement place, PsiElement dependency) {
      }
    });
    assertInstanceOf(ref.resolve(), PsiClass.class);
  }

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
