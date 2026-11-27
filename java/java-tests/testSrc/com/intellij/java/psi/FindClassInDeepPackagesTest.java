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
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class FindClassInDeepPackagesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    ModuleRootModificationUtil.setModuleSdk(heavyFixture.get().getModule(), null);
  }

  private void setUpLibrary(final String s) {
    Module module = heavyFixture.get().getModule();
    final String testRoot = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/deepPackages";
    VirtualFile classesRoot = WriteCommandAction.runWriteCommandAction(null, (Computable<VirtualFile>)() -> {
      String path = testRoot + "/" + s;
      path = path.replace(File.separatorChar, '/');
      return StandardFileSystems.local().refreshAndFindFileByPath(path);
    });
    assertNotNull(classesRoot);
    ModuleRootModificationUtil.addModuleLibrary(module, classesRoot.getUrl());
    IndexingTestUtil.waitUntilIndexesAreReady(module.getProject());
  }

  @Test
  public void testSRC() {
    setUpLibrary("classes");
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final JavaPsiFacade psiManager = fixture.getJavaFacade();
    final PsiClass classA = ReadAction.computeBlocking(
      () -> psiManager.findClass("a.a.a.a.e.f.i", GlobalSearchScope.moduleWithLibrariesScope(fixture.getModule())));
    assertNotNull(classA);
  }

  @Test
  public void test3() {
    setUpLibrary("classes2");
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final JavaPsiFacade psiManager = fixture.getJavaFacade();
    final PsiClass classA = ReadAction.computeBlocking(
      () -> psiManager.findClass("com.intellij.internal.f.a.b.a.i", GlobalSearchScope.moduleWithLibrariesScope(fixture.getModule())));
    assertNotNull(classA);
  }
}
