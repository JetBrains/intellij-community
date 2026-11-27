// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.scopes;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.codeInsight.daemon.impl.MarkerType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test ensuring searchers use {@link com.intellij.psi.search.PsiSearchHelper} to enable enlarging use scopes in plugins.
 * For this test, we will enlarge the use scope to include libraries.
 */
@TestApplication
public class LibraryUseSearchUsingScopeEnlargerTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/libraryScopeEnlarger/";

  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(TEST_DATA_PATH, false, moduleBuilder -> {
      moduleBuilder.addLibrary("lib", Map.of(OrderRootType.CLASSES, new String[]{TEST_DATA_PATH + "lib/classes"},
                                             OrderRootType.SOURCES, new String[]{TEST_DATA_PATH + "lib/src"}));
      moduleBuilder.addSourceContentRoot(TEST_DATA_PATH + "src");
      moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
      moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    });

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      Disposer.register(myFixture.getTestRootDisposable(), DynamicPluginTestUtilsKt.loadExtensionWithText(
        "<useScopeEnlarger implementation=\"com.intellij.java.scopes.LibraryUseSearchUsingScopeEnlargerTest$LibraryUseScopeEnlarger\"/>",
        "com.intellij"));
    });
    //bug? Test seems to use stale data.
    RefreshQueue.getInstance().refreshPaths(false, true, null, ContainerUtil.map(Collections.singleton(new File(myFixture.getTestDataPath())), File::toPath));
  }

  @Test
  public void testSearchFromScourceLooksInLibrary() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass sourceClass = myFixture.findClass("TestSrcInterface");
      PsiClass libImpl = myFixture.findClass("TestInterfaceImpl");
      Collection<PsiClass> classInheritors = ClassInheritorsSearch.search(sourceClass).findAll();

      assertThat(classInheritors).contains(libImpl);
    });
  }

  @Test
  public void testLineMarkersUseEnlarger() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass sourceClass = ReadAction.computeBlocking(() -> myFixture.findClass("TestSrcInterface"));

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.openFileInEditor(sourceClass.getContainingFile().getVirtualFile());
      myFixture.doHighlighting();
      Document document = myFixture.getDocument(sourceClass.getContainingFile());

      List<LineMarkerInfo<?>> lineMarkers = LineMarkersPass.getDisplayedLineMarkers(document, myFixture.getProject());
      assertEquals(2, lineMarkers.size());

      assertEquals(MarkerType.SUBCLASSED_CLASS.getNavigationHandler(), lineMarkers.get(0).getNavigationHandler());
      assertEquals(MarkerType.OVERRIDDEN_METHOD.getNavigationHandler(), lineMarkers.get(1).getNavigationHandler());
    });
  }

  @Test
  public void testOverridingMethodSearcherUsesEnlarger() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass sourceClass = myFixture.findClass("TestSrcInterface");
      PsiMethod testMethod = PsiTreeUtil.findChildOfType(sourceClass, PsiMethod.class);

      Collection<PsiMethod> methods = ReadAction.computeBlocking(
        () -> OverridingMethodsSearch.search(testMethod, GlobalSearchScope.allScope(myFixture.getProject()), false).findAll());
      assertEquals(1, methods.size());
    });
  }

  @InternalIgnoreDependencyViolation
  final static class LibraryUseScopeEnlarger extends UseScopeEnlarger {
    @Override
    public @Nullable SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
      return LibraryScopeCache.getInstance(element.getProject()).getLibrariesOnlyScope();
    }
  }
}
