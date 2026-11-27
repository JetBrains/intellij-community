// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectDependenciesAnalyzer;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class FindReachableExportedDependenciesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testModuleDependency() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    Module c = fixture.createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertThat(findReachableViaThisDependencyOnly(a, b)).singleElement().actual().getKey();
    assertSame(c, ((ModuleOrderEntry)dependency).getModule());
    ModuleRootModificationUtil.addDependency(a, c);
    assertThat(findReachableViaThisDependencyOnly(a, b)).isEmpty();
  }

  @Test
  public void testModuleDependencyNotExported() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    Module c = fixture.createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, c);
    assertThat(findReachableViaThisDependencyOnly(a, b)).isEmpty();
  }

  @Test
  public void testLibraryDependency() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    Library lib = PsiTestUtil.addProjectLibrary(fixture.getModule(), "lib", Arrays.asList(), Collections.emptyList());
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, lib, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertThat(findReachableViaThisDependencyOnly(a, b)).singleElement().actual().getKey();
    assertSame(lib, ((LibraryOrderEntry)dependency).getLibrary());
    ModuleRootModificationUtil.addDependency(a, lib);
    assertThat(findReachableViaThisDependencyOnly(a, b)).isEmpty();
  }

  @Test
  public void testLibraryDependencyNotExported() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    Library lib = PsiTestUtil.addProjectLibrary(fixture.getModule(), "lib", Arrays.asList(), Collections.emptyList());
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, lib);
    assertThat(findReachableViaThisDependencyOnly(a, b)).isEmpty();
  }

  @Test
  public void testTwoPaths() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b1 = fixture.createModule("b1");
    Module b2 = fixture.createModule("b2");
    Module c = fixture.createModule("c");
    ModuleRootModificationUtil.addDependency(a, b1);
    ModuleRootModificationUtil.addDependency(a, b2);
    ModuleRootModificationUtil.addDependency(b1, c, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(b2, c, DependencyScope.COMPILE, true);
    assertThat(findReachableViaThisDependencyOnly(a, b1)).isEmpty();
  }

  @Test
  public void testExportedOfExportedDependency() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    Module c = fixture.createModule("c");
    Module d = fixture.createModule("d");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(c, d, DependencyScope.COMPILE, true);
    List<Map.Entry<OrderEntry, OrderEntry>> result = new ArrayList<>(findReachableViaThisDependencyOnly(a, b));
    assertEquals(2, result.size());
    assertSame(c, ((ModuleOrderEntry)result.get(0).getKey()).getModule());
    assertSame(c, ((ModuleOrderEntry)result.get(0).getValue()).getModule());
    assertSame(d, result.get(1).getKey().getOwnerModule());
    assertSame(c, ((ModuleOrderEntry)result.get(1).getValue()).getModule());
  }

  @Test
  public void testIgnoreDirectRuntimeDependency() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    Module c = fixture.createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(a, c, DependencyScope.RUNTIME, false);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertThat(findReachableViaThisDependencyOnly(a, b)).singleElement().actual().getKey();
    assertSame(c, ((ModuleOrderEntry)dependency).getModule());
  }

  @Test
  public void testIgnoreDirectTestDependency() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    Module c = fixture.createModule("c");
    ModuleRootModificationUtil.addDependency(a, b);
    ModuleRootModificationUtil.addDependency(a, c, DependencyScope.TEST, false);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    OrderEntry dependency = assertThat(findReachableViaThisDependencyOnly(a, b)).singleElement().actual().getKey();
    assertSame(c, ((ModuleOrderEntry)dependency).getModule());
  }

  @Test
  public void testHonorDirectTestDependencyWhenAnalyzingTestDependency() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module a = fixture.createModule("a");
    Module b = fixture.createModule("b");
    Module c = fixture.createModule("c");
    ModuleRootModificationUtil.addDependency(a, b, DependencyScope.TEST, false);
    ModuleRootModificationUtil.addDependency(a, c, DependencyScope.TEST, false);
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.COMPILE, true);
    assertThat(findReachableViaThisDependencyOnly(a, b)).isEmpty();
  }

  private Set<Map.Entry<OrderEntry, OrderEntry>> findReachableViaThisDependencyOnly(Module a, Module b) {
    ModulesProvider rootModelProvider = DefaultModulesProvider.createForProject(heavyFixture.get().getProject());
    return JavaProjectDependenciesAnalyzer.findExportedDependenciesReachableViaThisDependencyOnly(a, b, rootModelProvider).entrySet();
  }
}
