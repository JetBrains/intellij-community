// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ExportingModulesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void test1() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    String rootPath = JavaTestUtil.getJavaTestDataPath().replace(File.separatorChar, '/') + "/moduleRootManager/exportedModules/";
    VirtualFile testRoot = StandardFileSystems.local().refreshAndFindFileByPath(rootPath);
    assertNotNull(testRoot);

    Path dir = ProjectKt.getStateStore(fixture.getProject()).getProjectBasePath();

    final Module[] moduleB = new Module[1];
    final Module[] moduleC = new Module[1];

    WriteAction.runAndWait(() -> {
      ModifiableModuleModel moduleModel = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
      Module moduleA = moduleModel.newModule(dir.resolve("A.iml"), JavaModuleType.getModuleType().getId());

      moduleB[0] = moduleModel.newModule(dir.resolve("B.iml"), JavaModuleType.getModuleType().getId());
      moduleC[0] = moduleModel.newModule(dir.resolve("C.iml"), JavaModuleType.getModuleType().getId());
      moduleModel.commit();

      configureModule(moduleA, testRoot, "A");
      configureModule(moduleB[0], testRoot, "B");
      configureModule(moduleC[0], testRoot, "C");

      ModuleRootModificationUtil.addDependency(moduleB[0], moduleA, DependencyScope.COMPILE, true);

      ModuleRootModificationUtil.addDependency(moduleC[0], moduleB[0]);
    });
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());

    ReadAction.runBlocking(() -> {
      final PsiClass pCClass =
        JavaPsiFacade.getInstance(fixture.getProject()).findClass("p.C", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC[0]));
      assertNotNull(pCClass);

      final PsiClass pAClass =
        JavaPsiFacade.getInstance(fixture.getProject()).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleB[0]));
      assertNotNull(pAClass);

      final PsiClass pAClass2 =
        JavaPsiFacade.getInstance(fixture.getProject()).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC[0]));
      assertNotNull(pAClass2);
    });
  }

  private static void configureModule(final Module module, final VirtualFile testRoot, final String name) {
    VirtualFile contentRoot = testRoot.findChild(name);
    PsiTestUtil.addContentRoot(module, contentRoot);
    PsiTestUtil.addSourceRoot(module, contentRoot.findChild("src"));
  }
}
