// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.easymock.IArgumentMatcher;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reportMatcher;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ResolveClassInModulesWithDependenciesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture =
    heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/psi/resolve/");

  @Test
  public void testTwoModules() {
    configureDependency();
    PsiReference ref = configure();
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertInstanceOf(PsiClass.class, target, String.valueOf(target));
    });
  }

  @Test
  public void testTwoModules2() {
    configureDependency();
    PsiReference ref = configure();
    PsiElement target = ReadAction.computeBlocking(() -> ((PsiJavaReference)ref).advancedResolve(true).getElement());
    assertNull(target);
  }

  @Test
  public void testTwoModulesJavadocRef() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module unrelated = WriteAction.computeAndWait(() -> {
      ModifiableModuleModel modifiableModel = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
      Module module = modifiableModel.newModule("b.iml", JavaModuleType.getModuleType().getId());
      modifiableModel.commit();
      return module;
    });
    fixture.createFile(unrelated, fixture.createTempVirtualDir(), "Src.java", "class Src {}");

    configureDependency();

    PsiReference configured = configure();
    PsiReference ref = configured instanceof PsiMultiReference polyRef ? polyRef.getReferences()[0] : configured;
    ReadAction.runBlocking(() -> {
      PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      assertNotNull(target);
      assertNotSame(unrelated, ModuleUtilCore.findModuleForPsiElement(target));
    });
  }

  @Test
  public void testModuleSourceAsLibrarySource() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile dir = fixture.createTempVirtualDir();
    fixture.createFile(fixture.getModule(), dir, "ModuleSourceAsLibrarySourceDep.java", fixture.loadFile("class/ModuleSourceAsLibrarySourceDep.java"));
    ModuleRootModificationUtil.addModuleLibrary(fixture.getModule(), "lib", Collections.emptyList(), Collections.singletonList(dir.getUrl()));

    final PsiReference ref = fixture.configureReferenceByFile("class/" + fixture.getTestName(false) + ".java", dir);
    ReadAction.runBlocking(() -> {
      final VirtualFile file = ref.getElement().getContainingFile().getVirtualFile();
      assertNotNull(file);

      assertInstanceOf(PsiClass.class, ref.resolve());
    });
  }

  @Test
  public void testModuleSourceAsLibraryClasses() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile dir = fixture.createTempVirtualDir();
    fixture.createFile(fixture.getModule(), dir, "ModuleSourceAsLibraryClassesDep.java", fixture.loadFile("class/ModuleSourceAsLibraryClassesDep.java"));
    ModuleRootModificationUtil.addModuleLibrary(fixture.getModule(), "lib", Collections.singletonList(dir.getUrl()), Collections.emptyList());

    PsiReference ref = fixture.configureReferenceByFile("class/" + fixture.getTestName(false) + ".java", dir);
    ReadAction.runBlocking(() -> {
      PsiFile psiFile = ref.getElement().getContainingFile();
      final VirtualFile file = psiFile.getVirtualFile();
      assertNotNull(file);
      //need this to ensure that PsiJavaFileBaseImpl.myResolveCache is filled to reproduce IDEA-91309
      DependenciesBuilder.analyzeFileDependencies(psiFile, (place, dependency) -> {
      });
      assertInstanceOf(PsiClass.class, ref.resolve());
    });
  }

  private void configureDependency() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    WriteAction.runAndWait(() -> {
      ModifiableModuleModel modifiableModel = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
      Module module = modifiableModel.newModule("a.iml", JavaModuleType.getModuleType().getId());
      modifiableModel.commit();

      VirtualFile root = StandardFileSystems.local().refreshAndFindFileByPath(fixture.getTestDataPath() + "/class/dependentModule");
      assert root != null;

      PsiTestUtil.addContentRoot(module, root);
      PsiTestUtil.addSourceRoot(module, root.findChild("src"));
      PsiTestUtil.addSourceRoot(module, root.findChild("test"), true);

      ModuleRootModificationUtil.addDependency(fixture.getModule(), module);
    });
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
  }

  private PsiReference configure() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    return fixture.configureReferenceByFile("class/" + fixture.getTestName(false) + ".java");
  }

  @Test
  public void testNoSubpackagesAccess() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    PsiElementFinder mock = createMockFinder();
    deregisterLombok();
    ExtensionPointImpl<PsiElementFinder> point = (ExtensionPointImpl<PsiElementFinder>)PsiElementFinder.EP.getPoint(fixture.getProject());
    point.maskAll(ContainerUtil.concat(point.getExtensionList(), Collections.singletonList(mock)), fixture.getTestRootDisposable(), false);

    PsiReference reference = configure();
    ReadAction.runBlocking(() -> {
      assertNull(reference.resolve());
      reference.getVariants();
    });

    verify(mock);
  }

  private void deregisterLombok() {
    ExtensionPointImpl<PsiAugmentProvider> augmentProviders = (ExtensionPointImpl<PsiAugmentProvider>)PsiAugmentProvider.EP_NAME.getPoint();
    List<PsiAugmentProvider> withoutLombok =
      ContainerUtil.filter(augmentProviders.getExtensionList(),
                           it -> !it.getClass().getName().equals("de.plushnikov.intellij.plugin.provider.LombokAugmentProvider"));
    augmentProviders.maskAll(withoutLombok, heavyFixture.get().getTestRootDisposable(), false);
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
