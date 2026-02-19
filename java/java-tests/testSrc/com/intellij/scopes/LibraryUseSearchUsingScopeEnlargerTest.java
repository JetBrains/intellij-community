// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.scopes;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.MarkerType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.platform.testFramework.DynamicPluginTestUtilsKt;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test ensuring searchers use {@link com.intellij.psi.search.PsiSearchHelper} to enable enlarging use scopes in plugins.
 * For this test, we will enlarge the use scope to include libraries.
 */
public class LibraryUseSearchUsingScopeEnlargerTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Disposer.register(getTestRootDisposable(), DynamicPluginTestUtilsKt.loadExtensionWithText(
      "<useScopeEnlarger implementation=\"com.intellij.scopes.LibraryUseSearchUsingScopeEnlargerTest$LibraryUseScopeEnlarger\"/>",
      "com.intellij"));
    //bug? Test seems to use stale data.
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(new File(getTestDataPath())), false, true, null);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);
    moduleBuilder.addLibrary("lib", Map.of(OrderRootType.CLASSES, new String[]{getTestDataPath() + "lib/classes"},
                                                              OrderRootType.SOURCES, new String[]{getTestDataPath() + "lib/src"}));
    moduleBuilder.addSourceContentRoot(getTestDataPath() + "src");
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testSearchFromScourceLooksInLibrary() {
    PsiClass sourceClass = myFixture.findClass("TestSrcInterface");
    PsiClass libImpl = myFixture.findClass("TestInterfaceImpl");
    Collection<PsiClass> classInheritors = ClassInheritorsSearch.search(sourceClass).findAll();

    assertContainsElements(classInheritors, libImpl);
  }

  @Test
  public void testLineMarkersUseEnlarger() {
    PsiClass sourceClass = myFixture.findClass("TestSrcInterface");

    myFixture.openFileInEditor(sourceClass.getContainingFile().getVirtualFile());
    myFixture.doHighlighting();
    Document document = myFixture.getDocument(sourceClass.getContainingFile());

    List<LineMarkerInfo<?>> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertSize(2, lineMarkers);

    assertEquals(MarkerType.SUBCLASSED_CLASS.getNavigationHandler(), lineMarkers.get(0).getNavigationHandler());
    assertEquals(MarkerType.OVERRIDDEN_METHOD.getNavigationHandler(), lineMarkers.get(1).getNavigationHandler());
  }

  @Test
  public void testOverridingMethodSearcherUsesEnlarger() {
    PsiClass sourceClass = myFixture.findClass("TestSrcInterface");
    PsiMethod testMethod = PsiTreeUtil.findChildOfType(sourceClass, PsiMethod.class);

    Collection<PsiMethod> methods = OverridingMethodsSearch.search(testMethod, GlobalSearchScope.allScope(getProject()), false).findAll();
    assertSize(1, methods);
  }

  @Override
  protected @NonNls String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/libraryScopeEnlarger/";
  }

  @Override
  protected boolean toAddSourceRoot() {
    return false;
  }

  @InternalIgnoreDependencyViolation
  final static class LibraryUseScopeEnlarger extends UseScopeEnlarger {
    @Override
    public @Nullable SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
      return LibraryScopeCache.getInstance(element.getProject()).getLibrariesOnlyScope();
    }
  }
}
