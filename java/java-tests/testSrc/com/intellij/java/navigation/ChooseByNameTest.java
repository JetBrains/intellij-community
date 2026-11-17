// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.searchEverywhere.SeItem;
import com.intellij.platform.searchEverywhere.SeProviderIdUtils;
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider;
import com.intellij.platform.searchEverywhere.testFramework.SeProvidersTestFixture;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeArgumentsProvider;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModes;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaIndexingModeCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FindSymbolParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@IndexingModes({IndexingMode.SMART, IndexingMode.DUMB_FULL_INDEX})
public class ChooseByNameTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  /** Tells which goto providers the test checks: the coroutine based ones, or the legacy based ones. */
  protected boolean isCoroutineBasedGoto() {
    return true;
  }

  private JavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    Registry.get(SeProvidersTestFixture.COROUTINE_BASED_GOTO_KEY).setValue(String.valueOf(isCoroutineBasedGoto()),
                                                                         fixture.getTestRootDisposable());
    // Some tests check the order of the items with an equal weight. A slow presentation must not change it.
    SeTargetItemsProvider.setPresentationOrderTimeoutForTests(Duration.ofMinutes(1), fixture.getTestRootDisposable());
  }

  /**
   * Runs {@code body} in {@code mode} on the EDT, as the JUnit 3 suite of the indexing modes did.
   */
  private void runInMode(IndexingMode mode, ThrowableRunnable<Exception> body) throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
      myFixture = JavaIndexingModeCodeInsightTestFixture.Companion.wrapFixture(fixture, mode);
      IndexingMode.ShutdownToken token = mode.setUpTest(fixture.getProject(), fixture.getTestRootDisposable());
      try {
        body.run();
      }
      finally {
        mode.tearDownTest(fixture.getProject(), token);
      }
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_goto_class_order_by_matching_degree(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass startMatch = myFixture.addClass("class UiUtil {}");
      PsiClass wordSkipMatch = myFixture.addClass("class UiAbstractUtil {}");
      PsiClass camelMatch = myFixture.addClass("class UberInstructionUxTopicInterface {}");
      PsiClass middleMatch = myFixture.addClass("class BaseUiUtil {}");
      List<PsiClass> elements = gotoClass("uiuti");
      assertIterableEquals(List.of(startMatch, wordSkipMatch, camelMatch, middleMatch), elements);
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_goto_file_order_by_matching_degree(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile camel = addEmptyFile("ServiceAccessor.java");
      PsiFile startLower = addEmptyFile("sache.txt");
      assertIterableEquals(List.of(camel, startLower), gotoFile("SA"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_disprefer_start_matches_when_prefix_starts_with_asterisk(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass startMatch = myFixture.addClass("class ITable {}");
      PsiClass endMatch = myFixture.addClass("class HappyHippoIT {}");
      PsiClass camelStartMatch = myFixture.addClass("class IntelligentTesting {}");
      PsiClass camelMiddleMatch = myFixture.addClass("class VeryIntelligentTesting {}");
      assertIterableEquals(List.of(endMatch, startMatch, camelStartMatch, camelMiddleMatch), gotoClass("*IT"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_annotation_syntax(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass match = myFixture.addClass("@interface Anno1 {}");
      myFixture.addClass("class Anno2 {}");
      assertEquals(match, gotoClass("@Anno").get(0));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_class_a_in_same_named_package_and_partially_matching_subpackage(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass c = myFixture.addClass("package com.intellij.codeInsight.template.impl; class TemplateListPanel {}");
      assertEquals(c, gotoClass("templistpa").get(0));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_no_result_for_empty_patterns(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      myFixture.addClass("@interface Anno1 {}");
      myFixture.addClass("class Anno2 {}");
      assertThat(gotoClass("")).isNullOrEmpty();
      assertThat(gotoClass("@")).isNullOrEmpty();
      assertThat(gotoFile("foo/")).isNullOrEmpty();
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_filter_overridden_methods_from_goto_symbol(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass intf = myFixture.addClass("""
                                           class Intf {
                                             void xxx1() {}
                                             void xxx2() {}
                                           }""");
      PsiClass impl = myFixture.addClass("""
                                           class Impl extends Intf {
                                             void xxx1() {}
                                             void xxx3() {}
                                           }""");

      List<PsiElement> elements = gotoSymbol("xxx");
      assertThat(elements).contains(intf.findMethodsByName("xxx1", false)[0]);
      assertThat(elements).contains(intf.findMethodsByName("xxx2", false)[0]);
      assertThat(elements).contains(impl.findMethodsByName("xxx3", false)[0]);
      if (DumbService.isDumb(myFixture.getProject())) { // in dumb mode overridden are also shown
        assertIterableEquals(List.of(impl.getMethods()[0], intf.getMethods()[0], intf.getMethods()[1], impl.getMethods()[1]), elements);
      } else {
        assertThat(elements).doesNotContain(impl.findMethodsByName("xxx1", false)[0]);
      }
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_goto_symbol_inner_class_dollar_sign(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiMethod[] method = myFixture.addClass("""
                                                  package pkg;
                                                  class Cls {
                                                    class Inner {
                                                      void paint() {}
                                                    }
                                                  }
                                                """).getInnerClasses()[0].getMethods();
      assertEquals(List.of(method), gotoSymbol("pkg.Cls.Inner.paint"));
      assertEquals(List.of(method), gotoSymbol("pkg.Cls$Inner.paint"));
      assertEquals(List.of(method), gotoSymbol("pkg.Cls$Inner#paint"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_goto_symbol_by_Copy_Reference_result(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiMethod[] methods = myFixture.addClass("""
                                                 package pkg;\s
                                                 import java.util.*;\s
                                                 class Cls {\s
                                                   void foo(int i) {}\s
                                                   void bar(int j) {}\s
                                                   void bar(boolean b) {}\s
                                                   void bar(List<String> l) {}\s
                                                 }""").getMethods();
      assertEquals(List.of(methods[0]), gotoSymbol("pkg.Cls.foo"));
      assertEquals(List.of(methods[0]), gotoSymbol("pkg.Cls#foo"));
      assertEquals(List.of(methods[0]), gotoSymbol("pkg.Cls#foo(int)"));
      assertEquals(Set.of(methods[1], methods[2], methods[3]), Set.copyOf(gotoSymbol("pkg.Cls.bar")));
      assertEquals(Set.of(methods[1], methods[2], methods[3]), Set.copyOf(gotoSymbol("pkg.Cls#bar")));
      assertEquals(List.of(methods[1]), gotoSymbol("pkg.Cls#bar(int)"));
      assertEquals(List.of(methods[2]), gotoSymbol("pkg.Cls#bar(boolean)"));
      assertEquals(List.of(methods[3]), gotoSymbol("pkg.Cls#bar(java.util.List)"));
      assertEquals(List.of(methods[3]), gotoSymbol("pkg.Cls#bar(java.util.List<java.lang.String>)"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_disprefer_underscore(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass intf = myFixture.addClass("""
                                           class Intf {
                                             void _xxx1() {}
                                             void xxx2() {}
                                           }""");
      List<PsiElement> elements = gotoSymbol("xxx");
      PsiMethod _xxx1 = intf.findMethodsByName("_xxx1", false)[0];
      PsiMethod xxx2 = intf.findMethodsByName("xxx2", false)[0];
      assertIterableEquals(List.of(xxx2, _xxx1), elements);
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_exact_extension_matches(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile m = addEmptyFile("relaunch.m");
      PsiFile mod = addEmptyFile("reference.mod");
      assertIterableEquals(List.of(m, mod), gotoFile("re*.m"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_exact_filename_match(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile i = addEmptyFile("foo/i.txt");
      PsiFile index = addEmptyFile("index.html");
      assertIterableEquals(List.of(i, index), gotoFile("i"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_shorter_filename_match(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile shorter = addEmptyFile("foo/cp-users.txt");
      PsiFile longer = addEmptyFile("cp-users-and-smth.html");
      assertIterableEquals(List.of(shorter, longer), gotoFile("cpusers"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_consider_dot_idea_files_out_of_project(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile outside = addEmptyFile(".idea/workspace.xml");
      PsiFile inside = addEmptyFile("workspace.txt");
      assertIterableEquals(List.of(inside), gotoFile("work", false));
      // The everywhere scope also holds the files of the IDE, such as the XHTML modules. Only the files of the test matter here.
      List<PsiFile> everywhere = ContainerUtil.filter(gotoFile("work", true), file -> file == inside || file == outside);
      assertIterableEquals(List.of(inside, outside), everywhere);
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_better_path_matches(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile fooIndex = myFixture.addFileToProject("foo/index.html", "foo");
      PsiFile fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar");
      PsiFile barFooIndex = myFixture.addFileToProject("bar/foo/index.html", "bar foo");
      assertIterableEquals(List.of(fooIndex, barFooIndex, fooBarIndex), gotoFile("foo/index"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_sort_same_named_items_by_path(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      List<PsiFile> files = new ArrayList<>();
      for (int i = 30; i >= 10; i--) {
        files.add(myFixture.addFileToProject("foo" + i + "/index.html", "foo" + i));
      }
      Collections.reverse(files);
      assertIterableEquals(files, gotoFile("index"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_middle_matching_for_files_and_directories(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile fooIndex = myFixture.addFileToProject("foo/index.html", "foo");
      PsiFile ooIndex = myFixture.addFileToProject("oo/index.html", "oo");
      PsiFile fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar");
      assertIterableEquals(List.of(ooIndex, fooIndex, fooBarIndex), gotoFile("oo/index"));
      assertIterableEquals(List.of(fooIndex, ooIndex, fooBarIndex), gotoFile("ndex.html"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_files_from_current_directory(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile fooIndex = myFixture.addFileToProject("foo/index.html", "foo");
      PsiFile barIndex = myFixture.addFileToProject("bar/index.html", "bar");
      PsiFile fooContext = addEmptyFile("foo/context.html");
      PsiFile barContext = addEmptyFile("bar/context.html");

      assertIterableEquals(List.of(fooIndex, barIndex), gotoFile("index", false, fooContext));
      assertIterableEquals(List.of(barIndex, fooIndex), gotoFile("index", false, barContext));
    });
  }

  private PsiFile addEmptyFile(String relativePath) {
    return myFixture.addFileToProject(relativePath, "");
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_accept_file_paths_starting_with_a_dot(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("foo/index.html");
      assertIterableEquals(List.of(file), gotoFile("./foo/in"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_don_t_match_path_to_jdk(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      List<PsiFile> objects = gotoFile("Object.java", true);
      assertThat(objects).isNotEmpty();
      assertTrue((objects.get(0)).getVirtualFile().getPath().contains("mockJDK"));
      assertThat(gotoFile("mockJDK/Object.java", true)).isNullOrEmpty();
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_goto_file_can_go_to_dir(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile fooIndex = addEmptyFile("foo/index.html");
      PsiFile barIndex = addEmptyFile("bar.txt/bar.txt");

      SeProvidersTestFixture fixture = createFixture(fooIndex);

      PsiDirectory fooDir = fooIndex.getContainingDirectory();
      PsiDirectory barDir = barIndex.getContainingDirectory();

      assertIterableEquals(List.of(fooDir), findFiles(fixture, "foo/"));
      assertIterableEquals(List.of(fooDir), findFiles(fixture, "foo\\"));
      assertIterableEquals(List.of(fooDir), findFiles(fixture, "/foo"));
      assertIterableEquals(List.of(fooDir), findFiles(fixture, "\\foo"));
      assertIterableEquals(List.of(fooDir), findFiles(fixture, "foo"));
      assertIterableEquals(List.of(fooIndex), findFiles(fixture, "/index.html"));
      assertIterableEquals(List.of(fooIndex), findFiles(fixture, "\\index.html"));
      assertThat(findFiles(fixture, "index.html/")).isNullOrEmpty();
      assertThat(findFiles(fixture, "index.html\\")).isNullOrEmpty();
      assertIterableEquals(List.of(barDir), findFiles(fixture, "bar.txt/"));
      assertIterableEquals(List.of(barDir), findFiles(fixture, "bar.txt\\"));
      assertIterableEquals(List.of(barIndex, barDir), findFiles(fixture, "/bar.txt"));
      assertIterableEquals(List.of(barIndex, barDir), findFiles(fixture, "\\bar.txt"));
      assertIterableEquals(List.of(barIndex, barDir), findFiles(fixture, "bar.txt"));
      assertIterableEquals(List.of(barIndex, barDir), findFiles(fixture, "bar"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_files_to_directories_even_if_longer(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile fooFile = addEmptyFile("dir/fooFile.txt");
      PsiDirectory fooDir = addEmptyFile("foo/barFile.txt").getContainingDirectory();

      assertIterableEquals(List.of(fooFile, fooDir), gotoFile("foo"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_find_method_by_qualified_name(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass clazz = myFixture.addClass("package foo.bar; class Goo { void zzzZzz() {} }");
      PsiMethod method = clazz.getMethods()[0];
      assertIterableEquals(List.of(method), gotoSymbol("zzzZzz"));
      assertIterableEquals(List.of(method), gotoSymbol("goo.zzzZzz"));
      assertIterableEquals(List.of(method), gotoSymbol("foo.bar.goo.zzzZzz"));
      assertIterableEquals(List.of(method), gotoSymbol("foo.zzzZzz"));
      assertIterableEquals(List.of(method), gotoSymbol("bar.zzzZzz"));
      assertIterableEquals(List.of(method), gotoSymbol("bar.goo.zzzZzz"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testFindRecordComponent(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass clazz = myFixture.addClass("package x.y.z; record Point(int momentous, int obsequious) {}");
      PsiRecordComponent[] components = clazz.getRecordComponents();
      assertIterableEquals(List.of(components[0]), gotoSymbol("mom"));
      assertIterableEquals(List.of(components[1]), gotoSymbol("obse"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_line_and_column_suffix(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass c = myFixture.addClass("package foo; class Bar {}");
      assertIterableEquals(List.of(c), gotoClass("Bar"));
      assertIterableEquals(List.of(c), gotoClass("Bar:2"));
      assertIterableEquals(List.of(c), gotoClass("Bar:2:3"));
      assertIterableEquals(List.of(c), gotoClass("Bar:[2:3]"));
      assertIterableEquals(List.of(c), gotoClass("Bar:[2,3]"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_custom_line_suffixes(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("Bar.txt");
      assertIterableEquals(List.of(file), gotoFile("Bar:2"));
      assertIterableEquals(List.of(file), gotoFile("Bar(2)"));
      assertIterableEquals(List.of(file), gotoFile("Bar on line 2"));
      assertIterableEquals(List.of(file), gotoFile("Bar at line 2"));
      assertIterableEquals(List.of(file), gotoFile("Bar 2:39"));
      assertIterableEquals(List.of(file), gotoFile("Bar#L2"));
      assertIterableEquals(List.of(file), gotoFile("Bar?l=2"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_dollar(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass bar = myFixture.addClass("package foo; class Bar { class Foo {} }");
      PsiClass foo = bar.getInnerClasses()[0];
      myFixture.addClass("package goo; class Goo { }");
      assertIterableEquals(List.of(foo), gotoClass("Bar$Foo"));
      assertIterableEquals(List.of(foo), gotoClass("foo.Bar$Foo"));
      assertIterableEquals(List.of(foo), gotoClass("foo.B$F"));
      assertThat(gotoClass("foo$Foo")).isNullOrEmpty();
      assertThat(gotoClass("foo$Bar")).isNullOrEmpty();
      assertThat(gotoClass("foo$Bar$Foo")).isNullOrEmpty();
      assertThat(gotoClass("foo$Goo")).isNullOrEmpty();
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_anonymous_classes(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass goo = myFixture.addClass("package goo; class Goo { Runnable r = new Runnable() {}; }");
      assertIterableEquals(List.of(goo), gotoClass("Goo$1"));
      assertIterableEquals(List.of(goo), gotoSymbol("Goo$1"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_qualified_name_matching(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass bar = myFixture.addClass("package foo.bar; class Bar { }");
      PsiClass bar2 = myFixture.addClass("package goo.baz; class Bar { }");
      assertIterableEquals(List.of(bar), gotoClass("foo.Bar"));
      assertIterableEquals(List.of(bar), gotoClass("foo.bar.Bar"));
      assertIterableEquals(List.of(bar2), gotoClass("goo.Bar"));
      assertIterableEquals(List.of(bar2), gotoClass("goo.baz.Bar"));
    });
  }

  /**
   * Keeps the Java methods named {@code run}.
   * Search Everywhere drops the trailing space of the search text, so "run " also finds the longer names.
   */
  private static List<PsiElement> filterJavaRunMethods(List<PsiElement> elems) {
    return new ArrayList<>(ContainerUtil.filter(elems, elem -> elem.getLanguage().equals(JavaLanguage.INSTANCE) &&
                                                               elem instanceof PsiMethod method && method.getName().equals("run")));
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_super_method_in_jdk(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass clazz = myFixture.addClass("""
                                            package foo.bar;
                                            class Goo implements Runnable {
                                              @Override
                                              public void run() { }
                                            }""");
      PsiMethod ourRun = clazz.getMethods()[0];
      PsiMethod sdkRun = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
        () -> ourRun.getContainingClass().getInterfaces()[0].getMethods()[0]
      );
      PsiMethod sdkRun2 = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
        () -> myFixture.findClass("java.security.PrivilegedAction").getMethods()[0]
      );
      PsiMethod sdkRun3 = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
        () -> myFixture.findClass("java.security.PrivilegedExceptionAction").getMethods()[0]
      );

      List<PsiElement> withLibs = filterJavaRunMethods(gotoSymbol("run ", true));
      withLibs.remove(sdkRun2);
      withLibs.remove(sdkRun3);
      if (!DumbService.isDumb(myFixture.getProject())) { // in dumb mode overridden are also shown
        assertIterableEquals(List.of(sdkRun), withLibs);
      }
      assertThat(withLibs).doesNotContain(ourRun);

      List<PsiElement> noLibs = filterJavaRunMethods(gotoSymbol("run ", false));
      assertIterableEquals(List.of(ourRun), noLibs);
      assertThat(noLibs).doesNotContain(sdkRun);
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_super_method_not_matching_query_qualifier(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass baseClass = myFixture.addClass("class Base { void xpaint() {} }");
      PsiClass subClass = myFixture.addClass("class Sub extends Base { void xpaint() {} }");
      PsiMethod base = baseClass.getMethods()[0];
      PsiMethod sub = subClass.getMethods()[0];
      assertIterableEquals(List.of(base), gotoSymbol("Ba.xpai", false));
      assertIterableEquals(List.of(sub), gotoSymbol("Su.xpai", false));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_groovy_script_class_with_non_identifier_name(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      GroovyFile file1 = (GroovyFile)addEmptyFile("foo.groovy");
      GroovyFile file2 = (GroovyFile)addEmptyFile("foo-bar.groovy");
      List<PsiElement> variants = gotoSymbol("foo", false);
      assertIterableEquals(List.of(file1.getScriptClass(), file2.getScriptClass()), variants);
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_exact_case_prefix_match_to_case_insensitive_name_match(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass caseInsensitiveName = myFixture.addClass("class XFile {}");
      PsiClass camelMatch = myFixture.addClass("class xfilterExprOwner {}");
      PsiClass exactCasePrefix = myFixture.addClass("class xfile_baton_t {}");
      assertIterableEquals(List.of(exactCasePrefix, caseInsensitiveName, camelMatch), gotoClass("xfile"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_prefix_match(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass wanted = myFixture.addClass("class PsiClassImpl {}");
      PsiClass smth = myFixture.addClass("class DroolsPsiClassImpl {}");
      assertIterableEquals(List.of(wanted, smth), gotoClass("PsiCl"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_out_of_project_content_files(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
        () -> myFixture.findClass(CommonClassNames.JAVA_LANG_OBJECT).getContainingFile()
      );
      List<PsiFile> elements = gotoFile("Object.class", true);
      assertThat(elements).contains(file);
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_classes_sorted_by_qualified_name_dispreferring_excluded_from_import_and_completion(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass foo = myFixture.addClass("package foo; class List {}");
      PsiClass bar = myFixture.addClass("package bar; class List {}");

      SeProvidersTestFixture fixture = createFixture(myFixture.addClass("class Context {}").getContainingFile());
      assertIterableEquals(List.of(bar, foo), findElements(fixture, SeProviderIdUtils.CLASSES_ID, "List", false));

      JavaProjectCodeInsightSettings.setExcludedNames(myFixture.getProject(), myFixture.getTestRootDisposable(), "bar");
      assertIterableEquals(List.of(foo, bar), findElements(fixture, SeProviderIdUtils.CLASSES_ID, "List", false));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_file_path_matching_without_slashes(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile fooBarFile = addEmptyFile("foo/bar/index_fooBar.html");
      PsiFile fbFile = addEmptyFile("fb/index_fb.html");
      PsiFile fbSomeFile = addEmptyFile("fb/some.dir/index_fbSome.html");
      PsiFile someFbFile = addEmptyFile("some/fb/index_someFb.html");

      assertIterableEquals(List.of(fooBarFile), gotoFile("barindex"));
      assertIterableEquals(List.of(fooBarFile), gotoFile("fooindex"));
      assertIterableEquals(List.of(fbFile, someFbFile, fbSomeFile, fooBarFile), gotoFile("fbindex"));
      assertIterableEquals(List.of(fbFile, someFbFile, fbSomeFile, fooBarFile), gotoFile("fbhtml"));

      // partial slashes
      assertIterableEquals(List.of(someFbFile), gotoFile("somefb/index.html"));
      assertIterableEquals(List.of(someFbFile), gotoFile("somefb\\index.html"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_file_path_matching_with_spaces_instead_of_slashes(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile good = addEmptyFile("config/app.txt");
      addEmptyFile("src/Configuration/ManagesApp.txt");
      assertEquals(good, gotoFile("config app.txt").get(0));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_multiple_slashes_in_goto_file(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("foo/bar/goo/file.txt");
      for (String path : List.of("foo/goo/file.txt", "foo/bar/file.txt", "bar/goo/file.txt", "foo/bar/goo/file.txt")) {
        assertIterableEquals(List.of(file), gotoFile(path));
        assertIterableEquals(List.of(file), gotoFile(path.replace("/", "\\")));
      }
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_show_matches_from_different_suffixes(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile enumControl = addEmptyFile("sample/EnumControl.java");
      PsiFile control = addEmptyFile("sample/ControlSmth.java");
      assertIterableEquals(List.of(control, enumControl), gotoFile("samplecontrol", false));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_show_longer_suffix_matches_from_jdk_and_shorter_from_project(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile seq = addEmptyFile("langc/Sequence.java");
      PsiClass charSeq = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
        () -> myFixture.findClass(CharSequence.class.getName())
      );
      assertIterableEquals(List.of(charSeq.getContainingFile(), seq), gotoFile("langcsequence", true));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_show_no_matches_from_jdk_when_there_are_in_project(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("String.txt");
      assertIterableEquals(List.of(file), gotoFile("Str", false));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_fix_keyboard_layout(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      // The everywhere scope also holds the classes of other languages, such as the String interface of TypeScript.
      PsiClass firstClass = ContainerUtil.findInstance(gotoClass("Ыекштп", true), PsiClass.class);
      assertNotNull(firstClass);
      assertEquals("String", firstClass.getName());
      @Nullable Object symbol =
        ContainerUtil.find(gotoSymbol("Ыекштп", true), sym -> sym instanceof PsiClass clazz && clazz.getName().equals("String"));
      assertNotNull(symbol);
      assertEquals("String.class", gotoFile("Ыекштп", true).get(0).getName());
      assertEquals("String.class", gotoFile("дфтпЫекштп", true).get(0).getName());
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_exact_case_match(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass upper = myFixture.addClass("package foo; class SOMECLASS {}");
      PsiClass camel = myFixture.addClass("package bar; class SomeClass {}");
      assertIterableEquals(List.of(camel, upper), gotoClass("SomeClass"));
      assertIterableEquals(List.of(camel.getContainingFile(), upper.getContainingFile()), gotoFile("SomeClass.java"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_closer_path_match(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile index = addEmptyFile("content/objc/features/index.html");
      PsiFile i18n = addEmptyFile("content/objc/features/screenshots/i18n.html");
      assertIterableEquals(List.of(index, i18n), gotoFile("objc/features/i"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_search_for_full_name(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file1 = addEmptyFile("Folder/Web/SubFolder/Flow.html");
      PsiFile file2 = addEmptyFile("Folder/Web/SubFolder/Flow/Helper.html");

      assertIterableEquals(List.of(file1, file2), withoutRepeats(gotoFile("Folder/Web/SubFolder/Flow.html")));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_name_match_over_path_match(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile nameMatchFile = addEmptyFile("JBCefBrowser.java");
      PsiFile pathMatchFile = addEmptyFile("com/elements/folder/WebBrowser.java");

      assertIterableEquals(List.of(nameMatchFile, pathMatchFile), withoutRepeats(gotoFile("CefBrowser")));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_matching_file_in_a_matching_directory(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("foo/index/index");
      assertIterableEquals(List.of(file, file.getParent()), gotoFile("in"));
      assertIterableEquals(List.of(file, file.getParent()), gotoFile("foin"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_fully_matching_module_name(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiJavaFile module = (PsiJavaFile)myFixture.addFileToProject("module-info.java", "module foo.bar {}");
      PsiClass clazz = myFixture.addClass("package foo; class B { void bar() {} void barX() {} }");
      assertIterableEquals(List.of(module.getModuleDeclaration(), clazz.getMethods()[0], clazz.getMethods()[1]), gotoSymbol("foo.bar"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_allow_name_separators_inside_wildcard(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass clazz = myFixture.addClass("package foo; class X { void bar() {} }");
      assertIterableEquals(List.of(clazz.getMethods()[0]), gotoSymbol("foo*bar"));
      assertIterableEquals(List.of(clazz), gotoClass("foo*X"));
      assertIterableEquals(List.of(clazz), gotoClass("X"));
      assertIterableEquals(List.of(clazz), gotoClass("foo.*"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_longer_name_vs_qualifier_matches(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass myInspection = myFixture.addClass("package ss; class MyInspection { }");
      PsiClass ssBasedInspection = myFixture.addClass("package foo; class SSBasedInspection { }");
      assertIterableEquals(List.of(ssBasedInspection, myInspection), gotoClass("ss*inspection"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_show_all_same_named_classes_sorted_by_qname(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass aFoo = myFixture.addClass("package a; class Foo { }");
      PsiClass bFoo = myFixture.addClass("package b; class Foo { }");
      PsiClass fooBar = myFixture.addClass("package c; class FooBar { }");
      assertIterableEquals(List.of(aFoo, bFoo, fooBar), gotoClass("Foo"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_show_prefix_matches_first_when_asterisk_is_in_the_middle(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass sb = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
        () -> myFixture.findClass(StringBuilder.class.getName())
      );
      PsiClass asb = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
        () -> myFixture.findClass("java.lang.AbstractStringBuilder")
      );
      assertIterableEquals(List.of(sb, asb), gotoClass("Str*Builder", true));
      assertIterableEquals(List.of(sb, asb), gotoClass("java.Str*Builder", true));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_include_overridden_qualified_name_method_matches(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiMethod m1 = myFixture.addClass("interface HttpRequest { void start() {} }").getMethods()[0];
      PsiMethod m2 = myFixture.addClass("interface Request extends HttpRequest { void start() {} }").getMethods()[0];
      // IJPL-220122: "Request.start" names m2 exactly, and names m1 only in part.
      assertIterableEquals(List.of(m2, m1), gotoSymbol("Request.start"));
      if (DumbService.getInstance(myFixture.getProject()).isDumb()) {
        assertIterableEquals(List.of(m1, m2), gotoSymbol("start")); // can't remove overrides in dumb mode
      }
      else {
        assertIterableEquals(List.of(m1), gotoSymbol("start"));
      }
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_colon_in_search_end(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass foo = myFixture.addClass("class Foo { }");
      assertIterableEquals(List.of(foo), gotoClass("Foo:"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_multi_word_class_name_with_only_first_letter_of_second_word(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      myFixture.addClass("class Foo { }");
      PsiClass fooBar = myFixture.addClass("class FooBar { }");
      assertIterableEquals(List.of(fooBar), gotoClass("Foo B"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_prefer_filename_match_regardless_of_package_match(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile f1 = addEmptyFile("resolve/ResolveCache.java");
      PsiFile f2 = addEmptyFile("abc/ResolveCacheSettings.xml");
      assertIterableEquals(List.of(f1, f2), gotoFile("resolvecache"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_search_for_long_full_name(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile veryLongNameFile = addEmptyFile("aaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbb/cccccccccccccccccc/" +
                                              "ddddddddddddddddd/eeeeeeeeeeeeeeee/ffffffffffffffffff/" +
                                              "ggggggggggggggggg/hhhhhhhhhhhhhhhh/ClassName.java");

      assertIterableEquals(List.of(veryLongNameFile), gotoFile("bbbbbbbbbbbbbbbb/cccccccccccccccccc/ddddddddddddddddd/eeeeeeeeeeeeeeee/" +
                                   "ffffffffffffffffff/ggggggggggggggggg/hhhhhhhhhhhhhhhh/ClassName.java"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_finds_the_file(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("foo/bar/goo/index.html");
      addEmptyFile("foo/index.html");
      assertIterableEquals(List.of(file), gotoFile("foo/bar/goo/index.html"));
    });
  }

  /** The literal lookup streams the fast results, and the walk of the project tree must return the same files. */
  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_literal_path_lookup_and_the_walk_return_the_same_files(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      addEmptyFile("foo/bar/goo/index.html");
      addEmptyFile("foo/bar/goo2/index.html");
      addEmptyFile("moduleA/foo/bar/goo/index.html");
      addEmptyFile("platform/core-util/foo/a.txt");
      addEmptyFile("platform/util/foo/a.txt");

      for (String pattern : List.of("foo/bar/goo/index.html", "platform/util/foo/a.txt", "foo/bar/goo/", "bar/goo/index.html")) {
        List<PsiFile> withLookup = gotoFile(pattern);
        withLiteralPathLookupDisabled(() -> assertThat(gotoFile(pattern)).containsExactlyInAnyOrderElementsOf(withLookup));
      }
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_finds_the_same_path_under_another_directory(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile atRoot = addEmptyFile("src/main/java/Foo.java");
      PsiFile nested = addEmptyFile("moduleA/src/main/java/Foo.java");
      assertThat(gotoFile("src/main/java/Foo.java")).containsExactlyInAnyOrderElementsOf(List.of(atRoot, nested));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_keeps_a_loosely_matching_directory(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile exact = addEmptyFile("main/java/util/Foo.java");
      PsiFile loose = addEmptyFile("main/javafx/util/Foo.java");
      assertIterableEquals(List.of(exact, loose), gotoFile("main/java/util/Foo.java"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_keeps_a_directory_that_only_a_subsequence_matches(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile exact = addEmptyFile("platform/util/foo/a.txt");
      PsiFile loose = addEmptyFile("platform/core-util/foo/a.txt");
      assertIterableEquals(List.of(exact, loose), gotoFile("platform/util/foo/a.txt"));
    });
  }

  /** The full name of a file in the popup starts with the name of its root, so a copied path can repeat it. */
  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_reads_the_root_name_both_ways(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile withoutPrefix = addEmptyFile("main/java/Foo.java");
      PsiFile withPrefix = addEmptyFile("src/main/java/Foo.java");
      assertThat(gotoFile("src/main/java/Foo.java")).containsExactlyInAnyOrderElementsOf(List.of(withoutPrefix, withPrefix));
    });
  }

  /**
   * The literal lookup resolves the path only to a library directory, so the project-only search finds no file there,
   * and the library offers one. The pass must still run the walk, which finds the file in a loosely matching directory.
   */
  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_in_a_library_keeps_the_walk_results(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      // the library must be outside the source root, which holds every file of the temp directory fixture
      VirtualFile tempRoot = ModuleRootManager.getInstance(myFixture.getModule()).getSourceRoots()[0].getParent();
      VirtualFile libRoot = VfsTestUtil.createDir(tempRoot, "lib");
      VfsTestUtil.createFile(libRoot, "foo/bar/goo/index.html");
      Library library = PsiTestUtil.addProjectLibrary(myFixture.getModule(), "lib", List.of(libRoot), List.of());
      PsiFile loose = addEmptyFile("foo/bar/goo2/index.html");
      try {
        assertIterableEquals(List.of(loose), gotoFile("foo/bar/goo/index.html"));
      }
      finally {
        PsiTestUtil.removeLibrary(myFixture.getModule(), library);
        WriteAction.run(() -> libRoot.delete(this));
      }
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_reports_each_file_once(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile exact = addEmptyFile("foo/bar/goo/index.html");
      PsiFile loose = addEmptyFile("foo/bar/goo2/index.html");
      List<PsiFile> result = gotoFile("foo/bar/goo/index.html");
      assertIterableEquals(List.of(exact, loose), result);
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_mis_cased_relative_path_still_finds_the_file(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("foo/bar/goo/index.html");
      assertIterableEquals(List.of(file), gotoFile("FOO/BAR/goo/index.html"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_with_backslashes_finds_the_file(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("foo/bar/goo/index.html");
      assertIterableEquals(List.of(file), gotoFile("foo\\bar\\goo\\index.html"));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_pasted_relative_path_of_a_directory_finds_the_directory(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiFile file = addEmptyFile("foo/bar/goo/doo/index.html");
      PsiDirectory dooDir = file.getContainingDirectory();

      assertIterableEquals(List.of(dooDir), gotoFile("foo/bar/goo/doo/", false, file));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_exactly_matching_qualified_symbol_gets_the_exact_match_degree(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiMethod method = myFixture.addClass("package foo; class Bar { void createBreakpoints() {} }").getMethods()[0];
      SeProvidersTestFixture fixture = createFixture(null);
      String symbols = SeProviderIdUtils.SYMBOLS_ID;

      int exactMatchDegree = DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE;
      assertTrue(weightOf(fixture, symbols, "foo.Bar#createBreakpoints", method) >= exactMatchDegree);
      // '#' and '.' are both name separators, so both spellings name the method exactly.
      assertTrue(weightOf(fixture, symbols, "foo.Bar.createBreakpoints", method) >= exactMatchDegree);
      // The user can leave out a part of the qualifier.
      assertTrue(weightOf(fixture, symbols, "Bar#createBreakpoints", method) >= exactMatchDegree);
      // A search text without a separator says nothing about the qualifier.
      assertTrue(weightOf(fixture, symbols, "createBreakpoints", method) < exactMatchDegree);
      // A partial name is not an exact match.
      assertTrue(weightOf(fixture, symbols, "foo.Bar#createBreak", method) < exactMatchDegree);
      // A Copy Reference result carries a signature. A signature is not a part of a name.
      assertTrue(weightOf(fixture, symbols, "foo.Bar#createBreakpoints()", method) >= exactMatchDegree);

      // The split Search Everywhere reads the exactness back from the weight, so the weight of a real
      // exact match must pass isInExactMatchDegreeRange.
      assertTrue(DefaultChooseByNameItemProvider.isInExactMatchDegreeRange(
        weightOf(fixture, symbols, "foo.Bar#createBreakpoints", method)));
      assertFalse(DefaultChooseByNameItemProvider.isInExactMatchDegreeRange(
        weightOf(fixture, symbols, "createBreakpoints", method)));
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_exactly_matching_qualified_class_gets_the_exact_match_degree(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass barClass = myFixture.addClass("package foo; class Bar { }");
      SeProvidersTestFixture fixture = createFixture(null);

      int exactMatchDegree = DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE;
      assertTrue(weightOf(fixture, SeProviderIdUtils.CLASSES_ID, "foo.Bar", barClass) >= exactMatchDegree);
      assertTrue(weightOf(fixture, SeProviderIdUtils.CLASSES_ID, "foo.Ba", barClass) < exactMatchDegree);
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void test_a_class_is_not_an_exact_match_for_a_member_search_text(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      PsiClass barClass = myFixture.addClass("package foo; class Bar { String baz; }");
      SeProvidersTestFixture fixture = createFixture(null);

      // The Classes tab drops "#baz" from the search text, because it navigates to the member after
      // it opens the class. The class alone does not answer the whole search text, so it must not
      // get the exact match degree.
      assertTrue(weightOf(fixture, SeProviderIdUtils.CLASSES_ID, "foo.Bar#baz", barClass) < DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE);
      // The same holds for an anonymous class search text.
      assertTrue(weightOf(fixture, SeProviderIdUtils.CLASSES_ID, "foo.Bar$1", barClass) < DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE);
    });
  }

  private static void withLiteralPathLookupDisabled(Runnable action) {
    RegistryValue registryValue = Registry.get("search.everywhere.goto.file.literal.directory.match");
    boolean previous = registryValue.asBoolean();
    registryValue.setValue(false);
    try {
      action.run();
    }
    finally {
      registryValue.setValue(previous);
    }
  }

  /** @return the best weight of the item */
  private static int weightOf(SeProvidersTestFixture fixture, String providerId, String text, Object item) {
    for (SeItem each : fixture.searchBlocking(providerId, text, false, false, ELEMENTS_LIMIT)) {
      if (item.equals(SeProvidersTestFixture.elementOf(each))) return each.weight();
    }
    throw new AssertionError("The search for '" + text + "' did not return " + item);
  }

  /** Opens a new Search Everywhere session. Its providers go away with the test. */
  private SeProvidersTestFixture createFixture(@Nullable PsiElement context) {
    return createFixture(myFixture.getTestRootDisposable(), context);
  }

  private SeProvidersTestFixture createFixture(Disposable parentDisposable, @Nullable PsiElement context) {
    return SeProvidersTestFixture.createBlocking(myFixture.getProject(), parentDisposable, ObjectUtils.tryCast(context, PsiFile.class));
  }

  private static List<?> findElements(SeProvidersTestFixture fixture, String providerId, String text, boolean everywhere) {
    return fixture.findElementsBlocking(providerId, text, everywhere, false, ELEMENTS_LIMIT);
  }

  private static List<?> findFiles(SeProvidersTestFixture fixture, String text) {
    return findElements(fixture, SeProviderIdUtils.FILES_ID, text, false);
  }

  /** Searches in a new session, as a new popup does, and closes the session after the search. */
  private List<?> findElements(String providerId, String text, boolean everywhere, @Nullable PsiElement context) {
    Disposable disposable = Disposer.newDisposable("ChooseByNameTest search");
    try {
      return findElements(createFixture(disposable, context), providerId, text, everywhere);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  /** @return the elements without their repeated copies. The search puts the best copy first, and this keeps it. */
  private static List<?> withoutRepeats(List<?> elements) {
    return new ArrayList<>(new LinkedHashSet<>(elements));
  }

  @SuppressWarnings("unchecked")
  private List<PsiClass> gotoClass(String text, boolean checkboxState, PsiElement context) {
    return (List<PsiClass>)findElements(SeProviderIdUtils.CLASSES_ID, text, checkboxState, context);
  }

  private List<PsiClass> gotoClass(String text, boolean checkboxState) {
    return gotoClass(text, checkboxState, null);
  }

  private List<PsiClass> gotoClass(String text) {
    return gotoClass(text, false, null);
  }

  @SuppressWarnings("unchecked")
  private List<PsiElement> gotoSymbol(String text, boolean checkboxState, PsiElement context) {
    return (List<PsiElement>)findElements(SeProviderIdUtils.SYMBOLS_ID, text, checkboxState, context);
  }

  private List<PsiElement> gotoSymbol(String text, boolean checkboxState) {
    return gotoSymbol(text, checkboxState, null);
  }

  private List<PsiElement> gotoSymbol(String text) {
    return gotoSymbol(text, false, null);
  }

  @SuppressWarnings("unchecked")
  private List<PsiFile> gotoFile(String text, boolean checkboxState, PsiElement context) {
    return (List<PsiFile>)findElements(SeProviderIdUtils.FILES_ID, text, checkboxState, context);
  }

  private List<PsiFile> gotoFile(String text, boolean checkboxState) {
    return gotoFile(text, checkboxState, null);
  }

  private List<PsiFile> gotoFile(String text) {
    return gotoFile(text, false, null);
  }

  public static List<?> calcContributorElements(SearchEverywhereContributor<?> contributor, String text) {
    return contributor.search(text, new MockProgressIndicator(), ELEMENTS_LIMIT).getItems();
  }

  public static SearchEverywhereContributor<Object> createFileContributor(Project project,
                                                                          Disposable parentDisposable,
                                                                          PsiElement context,
                                                                          boolean everywhere) {
    TestFileContributor res = new TestFileContributor(createEvent(project, context));
    res.setEverywhere(everywhere);
    Disposer.register(parentDisposable, res);
    return res;
  }

  public static SearchEverywhereContributor<Object> createFileContributor(Project project, Disposable parentDisposable) {
    return ChooseByNameTest.createFileContributor(project, parentDisposable, null, false);
  }

  public static AnActionEvent createEvent(Project project, PsiElement context) {
    assert project != null;
    DataContext dataContext = SimpleDataContext.getProjectContext(project);
    PsiFile file = ObjectUtils.tryCast(context, PsiFile.class);
    if (file != null) {
      dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_FILE, file, dataContext);
    }

    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext);
  }

  public static AnActionEvent createEvent(Project project) {
    return ChooseByNameTest.createEvent(project, null);
  }

  private static final Integer ELEMENTS_LIMIT = 30;

  private static class TestFileContributor extends FileSearchEverywhereContributor {
    private TestFileContributor(@NotNull AnActionEvent event) {
      super(event);
    }

    public void setEverywhere(boolean state) {
      myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, state));
    }

    @NotNull
    @Override
    public String getSearchProviderId() {
      return "FileSearchEverywhereContributor";
    }
  }
}
