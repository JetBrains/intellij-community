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
package com.intellij.java.openapi.roots.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ProjectLibrariesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile myRoot;
  private Library myLib;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();

    myRoot = StandardFileSystems.local().findFileByPath(JavaTestUtil.getJavaTestDataPath() + "/psi/cls/repo");
    assertNotNull(myRoot);

    myLib = WriteCommandAction.runWriteCommandAction(null,
                                                     (Computable<Library>)() -> LibraryTablesRegistrar.getInstance().getLibraryTable(fixture.getProject()).createLibrary("LIB"));
    ModuleRootModificationUtil.addDependency(fixture.getModule(), myLib);
  }

  @Test
  public void test() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    assertNull(ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(fixture.getModule()))));

    final Library.ModifiableModel model = myLib.getModifiableModel();
    WriteAction.runAndWait(() -> {
      model.addRoot(myRoot, OrderRootType.CLASSES);
      model.commit();
      IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
    });

    assertNotNull(ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(fixture.getModule()))));
  }

  @Test
  public void testNestedModelUpdate() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    assertNull(ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(fixture.getModule()))));

    final ModifiableRootModel moduleModel = ModuleRootManager.getInstance(fixture.getModule()).getModifiableModel();
    try {
      assertNotNull(moduleModel.findLibraryOrderEntry(myLib));

      final Library.ModifiableModel libModel = myLib.getModifiableModel();
      WriteAction.runAndWait(() -> {
        libModel.addRoot(myRoot, OrderRootType.CLASSES);
        libModel.commit();
        IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
      });

      assertNotNull(ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(fixture.getModule()))));
      assertTrue(ReadAction.computeBlocking(() -> Arrays.asList(moduleModel.orderEntries().librariesOnly().classes().getRoots()).contains(myRoot)));
    }
    finally {
      WriteAction.runAndWait(() -> {
        moduleModel.commit();
        IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
      });
    }

    assertNotNull(ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(fixture.getModule()))));
  }
}
