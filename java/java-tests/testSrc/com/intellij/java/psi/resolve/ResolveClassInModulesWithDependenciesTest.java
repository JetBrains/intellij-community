// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.JavaResolveTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.easymock.IArgumentMatcher;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.easymock.EasyMock.*;

public class ResolveClassInModulesWithDependenciesTest extends JavaResolveTestCase {
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

  public void testTwoModulesJavadocRef() throws Exception {
    Module unrelated = WriteAction.compute(() -> {
      ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      Module module = modifiableModel.newModule("b.iml", JavaModuleType.getModuleType().getId());
      modifiableModel.commit();
      return module;
    });
    createFile(unrelated, "Src.java", "class Src {}");

    configureDependency();

    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNotNull(target);
    assertNotSame(unrelated, ModuleUtilCore.findModuleForPsiElement(target));
  }

  public void testModuleSourceAsLibrarySource() throws Exception {
    VirtualFile dir = getTempDir().createVirtualDir();
    createFile(myModule, dir, "ModuleSourceAsLibrarySourceDep.java", loadFile("class/ModuleSourceAsLibrarySourceDep.java"));
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.emptyList(), Collections.singletonList(dir.getUrl()));

    final PsiReference ref = configureByFile("class/" + getTestName(false) + ".java", dir);
    final VirtualFile file = ref.getElement().getContainingFile().getVirtualFile();
    assertNotNull(file);

    assertInstanceOf(ref.resolve(), PsiClass.class);
  }

  public void testModuleSourceAsLibraryClasses() throws Exception {
    VirtualFile dir = getTempDir().createVirtualDir();
    createFile(myModule, dir, "ModuleSourceAsLibraryClassesDep.java", loadFile("class/ModuleSourceAsLibraryClassesDep.java"));
    ModuleRootModificationUtil.addModuleLibrary(myModule, "lib", Collections.singletonList(dir.getUrl()), Collections.emptyList());

    PsiReference ref = configureByFile("class/" + getTestName(false) + ".java", dir);
    PsiFile psiFile = ref.getElement().getContainingFile();
    final VirtualFile file = psiFile.getVirtualFile();
    assertNotNull(file);
    //need this to ensure that PsiJavaFileBaseImpl.myResolveCache is filled to reproduce IDEA-91309
    DependenciesBuilder.analyzeFileDependencies(psiFile, (place, dependency) -> {
    });
    assertInstanceOf(ref.resolve(), PsiClass.class);
  }

  private void configureDependency() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      Module module = modifiableModel.newModule("a.iml", JavaModuleType.getModuleType().getId());
      modifiableModel.commit();

      VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestDataPath() + "/class/dependentModule");
      assert root != null;

      PsiTestUtil.addContentRoot(module, root);
      PsiTestUtil.addSourceRoot(module, root.findChild("src"));
      PsiTestUtil.addSourceRoot(module, root.findChild("test"), true);

      ModuleRootModificationUtil.addDependency(getModule(), module);
    });
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
  }

  private PsiReference configure() throws Exception {
    return configureByFile("class/" + getTestName(false) + ".java");
  }

  public void testNoSubpackagesAccess() throws Exception {
    PsiElementFinder mock = createMockFinder();
    deregisterLombok();
    ExtensionPointImpl<PsiElementFinder> point = (ExtensionPointImpl<PsiElementFinder>)PsiElementFinder.EP.getPoint(myProject);
    point.maskAll(ContainerUtil.concat(point.getExtensionList(), Collections.singletonList(mock)), getTestRootDisposable(), false);

    PsiReference reference = configure();
    assertNull(reference.resolve());
    reference.getVariants();

    verify(mock);
  }

  private void deregisterLombok() {
    ExtensionPointImpl<PsiAugmentProvider> augmentProviders = (ExtensionPointImpl<PsiAugmentProvider>)PsiAugmentProvider.EP_NAME.getPoint();
    List<PsiAugmentProvider> withoutLombok =
      ContainerUtil.filter(augmentProviders.getExtensionList(),
                           it -> !it.getClass().getName().equals("de.plushnikov.intellij.plugin.provider.LombokAugmentProvider"));
    augmentProviders.maskAll(withoutLombok, getTestRootDisposable(), false);
  }

  private static PsiElementFinder createMockFinder() {
    Set<String> ignoredMethods = ContainerUtil.newHashSet("getClassesFilter", "processPackageDirectories", 
                                                          "processPackageFiles", "getClasses");
    Method[] methods = ContainerUtil.findAllAsArray(
      PsiElementFinder.class.getDeclaredMethods(),
      m -> !ignoredMethods.contains(m.getName()) && !Modifier.isPrivate(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()));
    PsiElementFinder mock = createMockBuilder(PsiElementFinder.class).addMockedMethods(methods).createMock();
    expect(mock.findClass(anyObject(), anyObject())).andReturn(null).anyTimes();
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
