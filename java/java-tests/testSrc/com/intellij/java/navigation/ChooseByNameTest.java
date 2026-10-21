// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestFrameworkUtil;
import com.intellij.testFramework.TestIndexingModeSupporter;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FindSymbolParameters;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("NewClassNamingConvention")
public class ChooseByNameTest extends LightJavaCodeInsightFixtureTestCase {

  private static final RegistryValue fuzzySearchRegistryValue = Registry.get("search.everywhere.fuzzy.file.search.enabled");
  private static final boolean isFuzzySearchEnabled = fuzzySearchRegistryValue.asBoolean();

  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTestSuite(ChooseByNameTest.class);
    TestIndexingModeSupporter.addTest(ChooseByNameTest.class, new TestIndexingModeSupporter.FullIndexSuite(), suite);
    return TestFrameworkUtil.flattenSuite(suite);
  }

  public void test_goto_class_order_by_matching_degree() {
    PsiClass startMatch = myFixture.addClass("class UiUtil {}");
    PsiClass wordSkipMatch = myFixture.addClass("class UiAbstractUtil {}");
    PsiClass camelMatch = myFixture.addClass("class UberInstructionUxTopicInterface {}");
    PsiClass middleMatch = myFixture.addClass("class BaseUiUtil {}");
    List<PsiClass> elements = gotoClass("uiuti");
    assertOrderedEquals(elements, List.of(startMatch, wordSkipMatch, camelMatch, middleMatch));
  }

  public void test_goto_file_order_by_matching_degree() {
    PsiFile camel = addEmptyFile("ServiceAccessor.java");
    PsiFile startLower = addEmptyFile("sache.txt");
    assertOrderedEquals(gotoFile("SA"), List.of(camel, startLower));
  }

  public void test_disprefer_start_matches_when_prefix_starts_with_asterisk() {
    PsiClass startMatch = myFixture.addClass("class ITable {}");
    PsiClass endMatch = myFixture.addClass("class HappyHippoIT {}");
    PsiClass camelStartMatch = myFixture.addClass("class IntelligentTesting {}");
    PsiClass camelMiddleMatch = myFixture.addClass("class VeryIntelligentTesting {}");
    assertOrderedEquals(gotoClass("*IT"), List.of(endMatch, startMatch, camelStartMatch, camelMiddleMatch));
  }

  public void test_annotation_syntax() {
    PsiClass match = myFixture.addClass("@interface Anno1 {}");
    myFixture.addClass("class Anno2 {}");
    assertEquals(match, gotoClass("@Anno").get(0));
  }

  public void test_class_a_in_same_named_package_and_partially_matching_subpackage() {
    PsiClass c = myFixture.addClass("package com.intellij.codeInsight.template.impl; class TemplateListPanel {}");
    assertEquals(c, gotoClass("templistpa").get(0));
  }

  public void test_no_result_for_empty_patterns() {
    myFixture.addClass("@interface Anno1 {}");
    myFixture.addClass("class Anno2 {}");
    assertEmpty(gotoClass(""));
    assertEmpty(gotoClass("@"));
    assertEmpty(gotoFile("foo/"));
  }

  public void test_filter_overridden_methods_from_goto_symbol() {
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
    assertContainsElements(elements, intf.findMethodsByName("xxx1", false)[0]);
    assertContainsElements(elements, intf.findMethodsByName("xxx2", false)[0]);
    assertContainsElements(elements, impl.findMethodsByName("xxx3", false)[0]);
    if (DumbService.isDumb(myFixture.getProject())) { // in dumb mode overridden are also shown
      assertOrderedEquals(elements, List.of(impl.getMethods()[0], intf.getMethods()[0], intf.getMethods()[1], impl.getMethods()[1]));
    } else {
      assertDoesntContain(elements, impl.findMethodsByName("xxx1", false)[0]);
    }
  }

  public void test_goto_symbol_inner_class_dollar_sign() {
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
  }

  public void test_goto_symbol_by_Copy_Reference_result() {
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
  }

  public void test_disprefer_underscore() {
    PsiClass intf = myFixture.addClass("""
                                         class Intf {
                                           void _xxx1() {}
                                           void xxx2() {}
                                         }""");
    List<PsiElement> elements = gotoSymbol("xxx");
    PsiMethod _xxx1 = intf.findMethodsByName("_xxx1", false)[0];
    PsiMethod xxx2 = intf.findMethodsByName("xxx2", false)[0];
    assertOrderedEquals(elements, List.of(xxx2, _xxx1));
  }

  public void test_prefer_exact_extension_matches() {
    PsiFile m = addEmptyFile("relaunch.m");
    PsiFile mod = addEmptyFile("reference.mod");
    assertOrderedEquals(gotoFile("re*.m"), List.of(m, mod));
  }

  public void test_prefer_exact_filename_match() {
    PsiFile i = addEmptyFile("foo/i.txt");
    PsiFile index = addEmptyFile("index.html");
    assertOrderedEquals(gotoFile("i"), List.of(i, index));
  }

  public void test_prefer_shorter_filename_match() {
    PsiFile shorter = addEmptyFile("foo/cp-users.txt");
    PsiFile longer = addEmptyFile("cp-users-and-smth.html");
    assertOrderedEquals(gotoFile("cpusers"), List.of(shorter, longer));
  }

  public void test_consider_dot_idea_files_out_of_project() {
    PsiFile outside = addEmptyFile(".idea/workspace.xml");
    PsiFile inside = addEmptyFile("workspace.txt");
    assertOrderedEquals(gotoFile("work", false), List.of(inside));
    assertOrderedEquals(gotoFile("work", true), List.of(inside, outside));
  }

  public void test_prefer_better_path_matches() {
    PsiFile fooIndex = myFixture.addFileToProject("foo/index.html", "foo");
    PsiFile fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar");
    PsiFile barFooIndex = myFixture.addFileToProject("bar/foo/index.html", "bar foo");
    assertOrderedEquals(gotoFile("foo/index"), List.of(fooIndex, barFooIndex, fooBarIndex));
  }

  public void test_sort_same_named_items_by_path() {
    List<PsiFile> files = new ArrayList<>();
    for (int i = 30; i >= 10; i--) {
      files.add(myFixture.addFileToProject("foo" + i + "/index.html", "foo" + i));
    }
    Collections.reverse(files);
    assertOrderedEquals(gotoFile("index"), files);
  }

  public void test_middle_matching_for_files_and_directories() {
    PsiFile fooIndex = myFixture.addFileToProject("foo/index.html", "foo");
    PsiFile ooIndex = myFixture.addFileToProject("oo/index.html", "oo");
    PsiFile fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar");
    assertOrderedEquals(gotoFile("oo/index"), List.of(ooIndex, fooIndex, fooBarIndex));
    assertOrderedEquals(gotoFile("ndex.html"), List.of(fooIndex, ooIndex, fooBarIndex));
  }

  public void test_prefer_files_from_current_directory() {
    PsiFile fooIndex = myFixture.addFileToProject("foo/index.html", "foo");
    PsiFile barIndex = myFixture.addFileToProject("bar/index.html", "bar");
    PsiFile fooContext = addEmptyFile("foo/context.html");
    PsiFile barContext = addEmptyFile("bar/context.html");

    SearchEverywhereContributor<Object> contributor = createFileContributor(getProject(), getTestRootDisposable(), fooContext);
    assertOrderedEquals(calcContributorElements(contributor, "index"), List.of(fooIndex, barIndex));

    contributor = createFileContributor(getProject(), getTestRootDisposable(), barContext);
    assertOrderedEquals(calcContributorElements(contributor, "index"), List.of(barIndex, fooIndex));
  }

  private PsiFile addEmptyFile(String relativePath) {
    return myFixture.addFileToProject(relativePath, "");
  }

  public void test_accept_file_paths_starting_with_a_dot() {
    PsiFile file = addEmptyFile("foo/index.html");
    assertOrderedEquals(gotoFile("./foo/in"), List.of(file));
  }

  public void test_don_t_match_path_to_jdk() {
    List<PsiFile> objects = gotoFile("Object.java", true);
    assertNotEmpty(objects);
    assertTrue((objects.get(0)).getVirtualFile().getPath().contains("mockJDK"));

    // Fuzzy search finds some results for the query `mockJDK/Object.java`, so let's ensure the test runs with fuzzy search disabled.
    fuzzySearchRegistryValue.setValue(false);

    assertEmpty(gotoFile("mockJDK/Object.java", true));

    fuzzySearchRegistryValue.setValue(isFuzzySearchEnabled);
  }

  public void test_goto_file_can_go_to_dir() {
    PsiFile fooIndex = addEmptyFile("foo/index.html");
    PsiFile barIndex = addEmptyFile("bar.txt/bar.txt");

    SearchEverywhereContributor<Object> contributor = createFileContributor(getProject(), getTestRootDisposable(), fooIndex);

    PsiDirectory fooDir = fooIndex.getContainingDirectory();
    PsiDirectory barDir = barIndex.getContainingDirectory();

    fuzzySearchRegistryValue.setValue(false);

    assertOrderedEquals(calcContributorElements(contributor, "foo/"), List.of(fooDir));
    assertOrderedEquals(calcContributorElements(contributor, "foo\\"), List.of(fooDir));
    assertOrderedEquals(calcContributorElements(contributor, "/foo"), List.of(fooDir));
    assertOrderedEquals(calcContributorElements(contributor, "\\foo"), List.of(fooDir));
    assertOrderedEquals(calcContributorElements(contributor, "foo"), List.of(fooDir));
    assertOrderedEquals(calcContributorElements(contributor, "/index.html"), List.of(fooIndex));
    assertOrderedEquals(calcContributorElements(contributor, "\\index.html"), List.of(fooIndex));
    assertEmpty(calcContributorElements(contributor, "index.html/"));
    assertEmpty(calcContributorElements(contributor, "index.html\\"));
    assertOrderedEquals(calcContributorElements(contributor, "bar.txt/"), List.of(barDir));
    assertOrderedEquals(calcContributorElements(contributor, "bar.txt\\"), List.of(barDir));
    assertOrderedEquals(calcContributorElements(contributor, "/bar.txt"), List.of(barIndex, barDir));
    assertOrderedEquals(calcContributorElements(contributor, "\\bar.txt"), List.of(barIndex, barDir));
    assertOrderedEquals(calcContributorElements(contributor, "bar.txt"), List.of(barIndex, barDir));
    assertOrderedEquals(calcContributorElements(contributor, "bar"), List.of(barIndex, barDir));

    fuzzySearchRegistryValue.setValue(isFuzzySearchEnabled);
  }

  public void test_goto_file_can_go_to_dir_with_fuzzy() {
    PsiFile fooIndex = addEmptyFile("foo/index.html");
    PsiFile barIndex = addEmptyFile("bar.txt/bar.txt");

    SearchEverywhereContributor<Object> contributor = createFileContributor(getProject(), getTestRootDisposable(), fooIndex);

    PsiDirectory fooDir = fooIndex.getContainingDirectory();
    PsiDirectory barDir = barIndex.getContainingDirectory();

    // With fuzzy search enabled, the search also shows files inside the directory.
    fuzzySearchRegistryValue.setValue(true);

    assertOrderedEquals(calcContributorElements(contributor, "foo/"), List.of(fooDir, fooIndex));
    assertOrderedEquals(calcContributorElements(contributor, "foo\\"), List.of(fooDir, fooIndex));
    assertOrderedEquals(calcContributorElements(contributor, "/foo"), List.of(fooDir, fooIndex));
    assertOrderedEquals(calcContributorElements(contributor, "\\foo"), List.of(fooDir, fooIndex));
    assertOrderedEquals(calcContributorElements(contributor, "foo"), List.of(fooDir, fooIndex));
    assertOrderedEquals(calcContributorElements(contributor, "/index.html"), List.of(fooIndex));
    assertOrderedEquals(calcContributorElements(contributor, "\\index.html"), List.of(fooIndex));
    assertEmpty(calcContributorElements(contributor, "index.html/"));
    assertEmpty(calcContributorElements(contributor, "index.html\\"));
    assertOrderedEquals(calcContributorElements(contributor, "bar.txt/"), List.of(barDir, barIndex));
    assertOrderedEquals(calcContributorElements(contributor, "bar.txt\\"), List.of(barDir, barIndex));
    assertOrderedEquals(calcContributorElements(contributor, "/bar.txt"), List.of(barIndex, barDir));
    assertOrderedEquals(calcContributorElements(contributor, "\\bar.txt"), List.of(barIndex, barDir));
    assertOrderedEquals(calcContributorElements(contributor, "bar.txt"), List.of(barIndex, barDir));
    assertOrderedEquals(calcContributorElements(contributor, "bar"), List.of(barIndex, barDir));

    fuzzySearchRegistryValue.setValue(isFuzzySearchEnabled);
  }

  public void test_prefer_files_to_directories_even_if_longer() {
    PsiFile fooFile = addEmptyFile("dir/fooFile.txt");
    PsiDirectory fooDir = addEmptyFile("foo/barFile.txt").getContainingDirectory();

    fuzzySearchRegistryValue.setValue(false);

    SearchEverywhereContributor<Object> contributor = createFileContributor(getProject(), getTestRootDisposable());
    List<?> popupElements = calcContributorElements(contributor, "foo");

    assertOrderedEquals(popupElements, List.of(fooFile, fooDir));

    fuzzySearchRegistryValue.setValue(isFuzzySearchEnabled);
  }

  public void test_prefer_files_to_directories_even_if_longer_with_fuzzy() {
    PsiFile fooFile = addEmptyFile("dir/fooFile.txt");
    PsiFile barFile = addEmptyFile("foo/barFile.txt");
    PsiDirectory fooDir = barFile.getContainingDirectory();

    // With fuzzy search enabled, the search also shows files inside the directory.
    fuzzySearchRegistryValue.setValue(true);

    SearchEverywhereContributor<Object> contributor = createFileContributor(getProject(), getTestRootDisposable());
    List<?> popupElements = calcContributorElements(contributor, "foo");

    assertOrderedEquals(popupElements, List.of(fooFile, fooDir, barFile));

    fuzzySearchRegistryValue.setValue(isFuzzySearchEnabled);
  }

  public void test_find_method_by_qualified_name() {
    PsiClass clazz = myFixture.addClass("package foo.bar; class Goo { void zzzZzz() {} }");
    PsiMethod method = clazz.getMethods()[0];
    assertOrderedEquals(gotoSymbol("zzzZzz"), List.of(method));
    assertOrderedEquals(gotoSymbol("goo.zzzZzz"), List.of(method));
    assertOrderedEquals(gotoSymbol("foo.bar.goo.zzzZzz"), List.of(method));
    assertOrderedEquals(gotoSymbol("foo.zzzZzz"), List.of(method));
    assertOrderedEquals(gotoSymbol("bar.zzzZzz"), List.of(method));
    assertOrderedEquals(gotoSymbol("bar.goo.zzzZzz"), List.of(method));
  }

  public void testFindRecordComponent() {
    PsiClass clazz = myFixture.addClass("package x.y.z; record Point(int momentous, int obsequious) {}");
    PsiRecordComponent[] components = clazz.getRecordComponents();
    assertOrderedEquals(gotoSymbol("mom"), components[0]);
    assertOrderedEquals(gotoSymbol("obse"), components[1]);
  }

  public void test_line_and_column_suffix() {
    PsiClass c = myFixture.addClass("package foo; class Bar {}");
    assertOrderedEquals(gotoClass("Bar"), List.of(c));
    assertOrderedEquals(gotoClass("Bar:2"), List.of(c));
    assertOrderedEquals(gotoClass("Bar:2:3"), List.of(c));
    assertOrderedEquals(gotoClass("Bar:[2:3]"), List.of(c));
    assertOrderedEquals(gotoClass("Bar:[2,3]"), List.of(c));
  }

  public void test_custom_line_suffixes() {
    PsiFile file = addEmptyFile("Bar.txt");
    assertOrderedEquals(gotoFile("Bar:2"), List.of(file));
    assertOrderedEquals(gotoFile("Bar(2)"), List.of(file));
    assertOrderedEquals(gotoFile("Bar on line 2"), List.of(file));
    assertOrderedEquals(gotoFile("Bar at line 2"), List.of(file));
    assertOrderedEquals(gotoFile("Bar 2:39"), List.of(file));
    assertOrderedEquals(gotoFile("Bar#L2"), List.of(file));
    assertOrderedEquals(gotoFile("Bar?l=2"), List.of(file));
  }

  public void test_dollar() {
    PsiClass bar = myFixture.addClass("package foo; class Bar { class Foo {} }");
    PsiClass foo = bar.getInnerClasses()[0];
    myFixture.addClass("package goo; class Goo { }");
    assertOrderedEquals(gotoClass("Bar$Foo"), List.of(foo));
    assertOrderedEquals(gotoClass("foo.Bar$Foo"), List.of(foo));
    assertOrderedEquals(gotoClass("foo.B$F"), List.of(foo));
    assertEmpty(gotoClass("foo$Foo"));
    assertEmpty(gotoClass("foo$Bar"));
    assertEmpty(gotoClass("foo$Bar$Foo"));
    assertEmpty(gotoClass("foo$Goo"));
  }

  public void test_anonymous_classes() {
    PsiClass goo = myFixture.addClass("package goo; class Goo { Runnable r = new Runnable() {}; }");
    assertOrderedEquals(gotoClass("Goo$1"), List.of(goo));
    assertOrderedEquals(gotoSymbol("Goo$1"), List.of(goo));
  }

  public void test_qualified_name_matching() {
    PsiClass bar = myFixture.addClass("package foo.bar; class Bar { }");
    PsiClass bar2 = myFixture.addClass("package goo.baz; class Bar { }");
    assertOrderedEquals(gotoClass("foo.Bar"), List.of(bar));
    assertOrderedEquals(gotoClass("foo.bar.Bar"), List.of(bar));
    assertOrderedEquals(gotoClass("goo.Bar"), List.of(bar2));
    assertOrderedEquals(gotoClass("goo.baz.Bar"), List.of(bar2));
  }

  private static List<PsiElement> filterJavaOnly(List<PsiElement> elems) {
    return new ArrayList<>(ContainerUtil.filter(elems, elem -> elem.getLanguage().equals(JavaLanguage.INSTANCE)));
  }

  public void test_super_method_in_jdk() {
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

    List<PsiElement> withLibs = filterJavaOnly(gotoSymbol("run ", true));
    withLibs.remove(sdkRun2);
    withLibs.remove(sdkRun3);
    if (!DumbService.isDumb(myFixture.getProject())) { // in dumb mode overridden are also shown
      assertOrderedEquals(withLibs, List.of(sdkRun));
    }
    assertDoesntContain(withLibs, ourRun);

    List<PsiElement> noLibs = filterJavaOnly(gotoSymbol("run ", false));
    assertOrderedEquals(noLibs, List.of(ourRun));
    assertDoesntContain(noLibs, sdkRun);
  }

  public void test_super_method_not_matching_query_qualifier() {
    PsiClass baseClass = myFixture.addClass("class Base { void xpaint() {} }");
    PsiClass subClass = myFixture.addClass("class Sub extends Base { void xpaint() {} }");
    PsiMethod base = baseClass.getMethods()[0];
    PsiMethod sub = subClass.getMethods()[0];
    assertOrderedEquals(gotoSymbol("Ba.xpai", false), List.of(base));
    assertOrderedEquals(gotoSymbol("Su.xpai", false), List.of(sub));
  }

  public void test_groovy_script_class_with_non_identifier_name() {
    GroovyFile file1 = (GroovyFile)addEmptyFile("foo.groovy");
    GroovyFile file2 = (GroovyFile)addEmptyFile("foo-bar.groovy");
    List<PsiElement> variants = gotoSymbol("foo", false);
    assertOrderedEquals(variants, List.of(file1.getScriptClass(), file2.getScriptClass()));
  }

  public void test_prefer_case_insensitive_exact_prefix_match() {
    PsiClass wanted = myFixture.addClass("class XFile {}");
    PsiClass smth1 = myFixture.addClass("class xfilterExprOwner {}");
    PsiClass smth2 = myFixture.addClass("class xfile_baton_t {}");
    SearchEverywhereContributor<Object> contributor = createClassContributor(getProject(), getTestRootDisposable());
    List<?> popupElements = calcContributorElements(contributor, "xfile");
    assertOrderedEquals(popupElements, List.of(wanted, smth2, smth1));
  }

  public void test_prefer_prefix_match() {
    PsiClass wanted = myFixture.addClass("class PsiClassImpl {}");
    PsiClass smth = myFixture.addClass("class DroolsPsiClassImpl {}");
    SearchEverywhereContributor<Object> contributor = createClassContributor(getProject(), getTestRootDisposable());
    List<?> popupElements = calcContributorElements(contributor, "PsiCl");
    assertOrderedEquals(popupElements, List.of(wanted, smth));
  }

  public void test_out_of_project_content_files() {
    PsiFile file = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
      () -> myFixture.findClass(CommonClassNames.JAVA_LANG_OBJECT).getContainingFile()
    );
    List<PsiFile> elements = gotoFile("Object.class", true);
    assertContainsElements(elements, file);
  }

  public void test_classes_sorted_by_qualified_name_dispreferring_excluded_from_import_and_completion() {
    PsiClass foo = myFixture.addClass("package foo; class List {}");
    PsiClass bar = myFixture.addClass("package bar; class List {}");

    SearchEverywhereContributor<Object> contributor =
      createClassContributor(getProject(), getTestRootDisposable(), myFixture.addClass("class Context {}").getContainingFile());
    assertOrderedEquals(calcContributorElements(contributor, "List"), List.of(bar, foo));

    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), getTestRootDisposable(), "bar");
    assertOrderedEquals(calcContributorElements(contributor, "List"), List.of(foo, bar));
  }

  public void test_file_path_matching_without_slashes() {
    PsiFile fooBarFile = addEmptyFile("foo/bar/index_fooBar.html");
    PsiFile fbFile = addEmptyFile("fb/index_fb.html");
    PsiFile fbSomeFile = addEmptyFile("fb/some.dir/index_fbSome.html");
    PsiFile someFbFile = addEmptyFile("some/fb/index_someFb.html");

    assertOrderedEquals(gotoFile("barindex"), List.of(fooBarFile));
    assertOrderedEquals(gotoFile("fooindex"), List.of(fooBarFile));
    assertOrderedEquals(gotoFile("fbindex"), List.of(fbFile, someFbFile, fbSomeFile, fooBarFile));
    assertOrderedEquals(gotoFile("fbhtml"), List.of(fbFile, someFbFile, fbSomeFile, fooBarFile));

    // partial slashes
    assertOrderedEquals(gotoFile("somefb/index.html"), List.of(someFbFile));
    assertOrderedEquals(gotoFile("somefb\\index.html"), List.of(someFbFile));
  }

  public void test_file_path_matching_with_spaces_instead_of_slashes() {
    PsiFile good = addEmptyFile("config/app.txt");
    addEmptyFile("src/Configuration/ManagesApp.txt");
    assertEquals(good, gotoFile("config app.txt").get(0));
  }

  public void test_multiple_slashes_in_goto_file() {
    PsiFile file = addEmptyFile("foo/bar/goo/file.txt");
    for (String path : List.of("foo/goo/file.txt", "foo/bar/file.txt", "bar/goo/file.txt", "foo/bar/goo/file.txt")) {
      assertOrderedEquals(gotoFile(path), List.of(file));
      assertOrderedEquals(gotoFile(path.replace("/", "\\")), List.of(file));
    }
  }

  public void test_show_matches_from_different_suffixes() {
    PsiFile enumControl = addEmptyFile("sample/EnumControl.java");
    PsiFile control = addEmptyFile("sample/ControlSmth.java");
    assertOrderedEquals(gotoFile("samplecontrol", false), List.of(enumControl, control));
  }

  public void test_show_longer_suffix_matches_from_jdk_and_shorter_from_project() {
    PsiFile seq = addEmptyFile("langc/Sequence.java");
    PsiClass charSeq = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
      () -> myFixture.findClass(CharSequence.class.getName())
    );
    assertOrderedEquals(gotoFile("langcsequence", true), List.of(charSeq.getContainingFile(), seq));
  }

  public void test_show_no_matches_from_jdk_when_there_are_in_project() {
    PsiFile file = addEmptyFile("String.txt");
    assertOrderedEquals(gotoFile("Str", false), List.of(file));
  }

  public void test_fix_keyboard_layout() {
    assertEquals("String", gotoClass("Ыекштп", true).get(0).getName());
    @Nullable Object symbol =
      ContainerUtil.find(gotoSymbol("Ыекштп", true), sym -> sym instanceof PsiClass clazz && clazz.getName().equals("String"));
    assertNotNull(symbol);
    assertEquals("String.class", gotoFile("Ыекштп", true).get(0).getName());
    assertEquals("String.class", gotoFile("дфтпЫекштп", true).get(0).getName());
  }

  public void test_prefer_exact_case_match() {
    PsiClass upper = myFixture.addClass("package foo; class SOMECLASS {}");
    PsiClass camel = myFixture.addClass("package bar; class SomeClass {}");
    assertOrderedEquals(gotoClass("SomeClass"), List.of(camel, upper));
    assertOrderedEquals(gotoFile("SomeClass.java"), List.of(camel.getContainingFile(), upper.getContainingFile()));
  }

  public void test_prefer_closer_path_match() {
    PsiFile index = addEmptyFile("content/objc/features/index.html");
    PsiFile i18n = addEmptyFile("content/objc/features/screenshots/i18n.html");
    assertOrderedEquals(gotoFile("objc/features/i"), List.of(index, i18n));
  }

  public void test_search_for_full_name() {
    PsiFile file1 = addEmptyFile("Folder/Web/SubFolder/Flow.html");
    PsiFile file2 = addEmptyFile("Folder/Web/SubFolder/Flow/Helper.html");

    SearchEverywhereContributor<Object> contributor = createFileContributor(getProject(), getTestRootDisposable());
    List<Object> files =
      calcWeightedContributorElements((WeightedSearchEverywhereContributor<?>)contributor, "Folder/Web/SubFolder/Flow.html");
    assertOrderedEquals(files, List.of(file1, file2));
  }

  public void test_prefer_name_match_over_path_match() {
    PsiFile nameMatchFile = addEmptyFile("JBCefBrowser.java");
    PsiFile pathMatchFile = addEmptyFile("com/elements/folder/WebBrowser.java");

    SearchEverywhereContributor<Object> contributor = createFileContributor(getProject(), getTestRootDisposable());
    List<Object> files = calcWeightedContributorElements((WeightedSearchEverywhereContributor<?>)contributor, "CefBrowser");
    assertOrderedEquals(files, List.of(nameMatchFile, pathMatchFile));
  }

  public void test_matching_file_in_a_matching_directory() {
    PsiFile file = addEmptyFile("foo/index/index");
    assertOrderedEquals(gotoFile("in"), List.of(file, file.getParent()));
    assertOrderedEquals(gotoFile("foin"), List.of(file, file.getParent()));
  }

  public void test_prefer_fully_matching_module_name() {
    PsiJavaFile module = (PsiJavaFile)myFixture.addFileToProject("module-info.java", "module foo.bar {}");
    PsiClass clazz = myFixture.addClass("package foo; class B { void bar() {} void barX() {} }");
    assertOrderedEquals(gotoSymbol("foo.bar"), List.of(module.getModuleDeclaration(), clazz.getMethods()[0], clazz.getMethods()[1]));
  }

  public void test_allow_name_separators_inside_wildcard() {
    PsiClass clazz = myFixture.addClass("package foo; class X { void bar() {} }");
    assertOrderedEquals(gotoSymbol("foo*bar"), List.of(clazz.getMethods()[0]));
    assertOrderedEquals(gotoClass("foo*X"), List.of(clazz));
    assertOrderedEquals(gotoClass("X"), List.of(clazz));
    assertOrderedEquals(gotoClass("foo.*"), List.of(clazz));
  }

  public void test_prefer_longer_name_vs_qualifier_matches() {
    PsiClass myInspection = myFixture.addClass("package ss; class MyInspection { }");
    PsiClass ssBasedInspection = myFixture.addClass("package foo; class SSBasedInspection { }");
    assertOrderedEquals(gotoClass("ss*inspection"), List.of(ssBasedInspection, myInspection));
  }

  public void test_show_all_same_named_classes_sorted_by_qname() {
    PsiClass aFoo = myFixture.addClass("package a; class Foo { }");
    PsiClass bFoo = myFixture.addClass("package b; class Foo { }");
    PsiClass fooBar = myFixture.addClass("package c; class FooBar { }");
    assertOrderedEquals(gotoClass("Foo"), List.of(aFoo, bFoo, fooBar));
  }

  public void test_show_prefix_matches_first_when_asterisk_is_in_the_middle() {
    PsiClass sb = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
      () -> myFixture.findClass(StringBuilder.class.getName())
    );
    PsiClass asb = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
      () -> myFixture.findClass("java.lang.AbstractStringBuilder")
    );
    assertOrderedEquals(gotoClass("Str*Builder", true), List.of(sb, asb));
    assertOrderedEquals(gotoClass("java.Str*Builder", true), List.of(sb, asb));
  }

  public void test_include_overridden_qualified_name_method_matches() {
    PsiMethod m1 = myFixture.addClass("interface HttpRequest { void start() {} }").getMethods()[0];
    PsiMethod m2 = myFixture.addClass("interface Request extends HttpRequest { void start() {} }").getMethods()[0];
    // IJPL-220122: "Request.start" names m2 exactly, and names m1 only in part.
    assertOrderedEquals(gotoSymbol("Request.start"), List.of(m2, m1));
    if (DumbService.getInstance(myFixture.getProject()).isDumb()) {
      assertOrderedEquals(gotoSymbol("start"), List.of(m1, m2)); // can't remove overrides in dumb mode
    }
    else {
      assertOrderedEquals(gotoSymbol("start"), List.of(m1));
    }
  }

  public void test_colon_in_search_end() {
    PsiClass foo = myFixture.addClass("class Foo { }");
    assertOrderedEquals(gotoClass("Foo:"), List.of(foo));
  }

  public void test_multi_word_class_name_with_only_first_letter_of_second_word() {
    myFixture.addClass("class Foo { }");
    PsiClass fooBar = myFixture.addClass("class FooBar { }");
    assertOrderedEquals(gotoClass("Foo B"), List.of(fooBar));
  }

  public void test_prefer_filename_match_regardless_of_package_match() {
    PsiFile f1 = addEmptyFile("resolve/ResolveCache.java");
    PsiFile f2 = addEmptyFile("abc/ResolveCacheSettings.xml");
    assertOrderedEquals(gotoFile("resolvecache"), List.of(f1, f2));
  }

  public void test_search_for_long_full_name() {
    PsiFile veryLongNameFile = addEmptyFile("aaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbb/cccccccccccccccccc/" +
                                            "ddddddddddddddddd/eeeeeeeeeeeeeeee/ffffffffffffffffff/" +
                                            "ggggggggggggggggg/hhhhhhhhhhhhhhhh/ClassName.java");

    assertOrderedEquals(gotoFile("bbbbbbbbbbbbbbbb/cccccccccccccccccc/ddddddddddddddddd/eeeeeeeeeeeeeeee/" +
                                 "ffffffffffffffffff/ggggggggggggggggg/hhhhhhhhhhhhhhhh/ClassName.java"), List.of(veryLongNameFile));
  }

  public void test_pasted_relative_path_finds_the_file() {
    PsiFile file = addEmptyFile("foo/bar/goo/index.html");
    addEmptyFile("foo/index.html");
    assertOrderedEquals(gotoFile("foo/bar/goo/index.html"), List.of(file));
  }

  /** The literal lookup streams the fast results, and the walk of the project tree must return the same files. */
  public void test_literal_path_lookup_and_the_walk_return_the_same_files() {
    addEmptyFile("foo/bar/goo/index.html");
    addEmptyFile("foo/bar/goo2/index.html");
    addEmptyFile("moduleA/foo/bar/goo/index.html");
    addEmptyFile("platform/core-util/foo/a.txt");
    addEmptyFile("platform/util/foo/a.txt");

    for (String pattern : List.of("foo/bar/goo/index.html", "platform/util/foo/a.txt", "foo/bar/goo/", "bar/goo/index.html")) {
      List<PsiFile> withLookup = gotoFile(pattern);
      withLiteralPathLookupDisabled(() -> assertSameElements(gotoFile(pattern), withLookup));
    }
  }

  public void test_pasted_relative_path_finds_the_same_path_under_another_directory() {
    PsiFile atRoot = addEmptyFile("src/main/java/Foo.java");
    PsiFile nested = addEmptyFile("moduleA/src/main/java/Foo.java");
    assertSameElements(gotoFile("src/main/java/Foo.java"), List.of(atRoot, nested));
  }

  public void test_pasted_relative_path_keeps_a_loosely_matching_directory() {
    PsiFile exact = addEmptyFile("main/java/util/Foo.java");
    PsiFile loose = addEmptyFile("main/javafx/util/Foo.java");
    assertOrderedEquals(gotoFile("main/java/util/Foo.java"), List.of(exact, loose));
  }

  public void test_pasted_relative_path_keeps_a_directory_that_only_a_subsequence_matches() {
    PsiFile exact = addEmptyFile("platform/util/foo/a.txt");
    PsiFile loose = addEmptyFile("platform/core-util/foo/a.txt");
    assertOrderedEquals(gotoFile("platform/util/foo/a.txt"), List.of(exact, loose));
  }

  /** The full name of a file in the popup starts with the name of its root, so a copied path can repeat it. */
  public void test_pasted_relative_path_reads_the_root_name_both_ways() {
    PsiFile withoutPrefix = addEmptyFile("main/java/Foo.java");
    PsiFile withPrefix = addEmptyFile("src/main/java/Foo.java");
    assertSameElements(gotoFile("src/main/java/Foo.java"), List.of(withoutPrefix, withPrefix));
  }

  /**
   * The literal lookup resolves the path only to a library directory, so the project-only search finds no file there,
   * and the library offers one. The pass must still run the walk, which finds the file in a loosely matching directory.
   */
  public void test_pasted_relative_path_in_a_library_keeps_the_walk_results() throws IOException {
    // the library must be outside the source root, which holds every file of the temp directory fixture
    VirtualFile tempRoot = ModuleRootManager.getInstance(getModule()).getSourceRoots()[0].getParent();
    VirtualFile libRoot = VfsTestUtil.createDir(tempRoot, "lib");
    VfsTestUtil.createFile(libRoot, "foo/bar/goo/index.html");
    Library library = PsiTestUtil.addProjectLibrary(getModule(), "lib", List.of(libRoot), List.of());
    PsiFile loose = addEmptyFile("foo/bar/goo2/index.html");
    fuzzySearchRegistryValue.setValue(false);
    try {
      assertOrderedEquals(gotoFile("foo/bar/goo/index.html"), List.of(loose));
    }
    finally {
      fuzzySearchRegistryValue.setValue(isFuzzySearchEnabled);
      PsiTestUtil.removeLibrary(getModule(), library);
      WriteAction.run(() -> libRoot.delete(this));
    }
  }

  public void test_pasted_relative_path_reports_each_file_once() {
    PsiFile exact = addEmptyFile("foo/bar/goo/index.html");
    PsiFile loose = addEmptyFile("foo/bar/goo2/index.html");
    List<PsiFile> result = gotoFile("foo/bar/goo/index.html");
    assertOrderedEquals(result, List.of(exact, loose));
  }

  public void test_mis_cased_relative_path_still_finds_the_file() {
    PsiFile file = addEmptyFile("foo/bar/goo/index.html");
    assertOrderedEquals(gotoFile("FOO/BAR/goo/index.html"), List.of(file));
  }

  public void test_pasted_relative_path_with_backslashes_finds_the_file() {
    PsiFile file = addEmptyFile("foo/bar/goo/index.html");
    assertOrderedEquals(gotoFile("foo\\bar\\goo\\index.html"), List.of(file));
  }

  public void test_pasted_relative_path_of_a_directory_finds_the_directory() {
    PsiFile file = addEmptyFile("foo/bar/goo/doo/index.html");
    PsiDirectory dooDir = file.getContainingDirectory();

    SearchEverywhereContributor<Object> contributor = createFileContributor(getProject(), getTestRootDisposable(), file);
    fuzzySearchRegistryValue.setValue(false);
    try {
      assertOrderedEquals(calcContributorElements(contributor, "foo/bar/goo/doo/"), List.of(dooDir));
    }
    finally {
      fuzzySearchRegistryValue.setValue(isFuzzySearchEnabled);
    }
  }

  /**
   * The search reports a file in the only matched directory twice: once by its name, and once as a child of that
   * directory. A long gap in the qualifier can put the name weight below the child weight, so the child copy must go through.
   * The gap must be longer than about a thousand characters to get there.
   */
  public void test_a_repeated_file_keeps_its_better_weight() {
    PsiFile file = addEmptyFile("shop/" + ("a".repeat(250) + "/").repeat(6) + "billing/billing.txt");
    WeightedSearchEverywhereContributor<?> contributor =
      (WeightedSearchEverywhereContributor<?>)createFileContributor(getProject(), getTestRootDisposable(), file);
    fuzzySearchRegistryValue.setValue(true);
    try {
      List<Integer> weights = weightsOf(contributor, "shop/billing", file);
      assertTrue("the name weight must be below the child weight: " + weights, weights.getFirst() < 0);
      assertEquals(weights.toString(), 0, (int)Collections.max(weights));
    }
    finally {
      fuzzySearchRegistryValue.setValue(isFuzzySearchEnabled);
    }
  }

  public void test_exactly_matching_qualified_symbol_gets_the_exact_match_degree() {
    PsiMethod method = myFixture.addClass("package foo; class Bar { void createBreakpoints() {} }").getMethods()[0];
    WeightedSearchEverywhereContributor<?> contributor =
      (WeightedSearchEverywhereContributor<?>)createSymbolContributor(getProject(), getTestRootDisposable(), null, false);

    int exactMatchDegree = DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE;
    assertTrue(weightOf(contributor, "foo.Bar#createBreakpoints", method) >= exactMatchDegree);
    // '#' and '.' are both name separators, so both spellings name the method exactly.
    assertTrue(weightOf(contributor, "foo.Bar.createBreakpoints", method) >= exactMatchDegree);
    // The user can leave out a part of the qualifier.
    assertTrue(weightOf(contributor, "Bar#createBreakpoints", method) >= exactMatchDegree);
    // A search text without a separator says nothing about the qualifier.
    assertTrue(weightOf(contributor, "createBreakpoints", method) < exactMatchDegree);
    // A partial name is not an exact match.
    assertTrue(weightOf(contributor, "foo.Bar#createBreak", method) < exactMatchDegree);
    // A Copy Reference result carries a signature. A signature is not a part of a name.
    assertTrue(weightOf(contributor, "foo.Bar#createBreakpoints()", method) >= exactMatchDegree);

    // The split Search Everywhere reads the exactness back from the weight, so the weight of a real
    // exact match must pass isInExactMatchDegreeRange.
    assertTrue(DefaultChooseByNameItemProvider.isInExactMatchDegreeRange(
      weightOf(contributor, "foo.Bar#createBreakpoints", method)));
    assertFalse(DefaultChooseByNameItemProvider.isInExactMatchDegreeRange(
      weightOf(contributor, "createBreakpoints", method)));
  }

  public void test_exactly_matching_qualified_class_gets_the_exact_match_degree() {
    PsiClass barClass = myFixture.addClass("package foo; class Bar { }");
    WeightedSearchEverywhereContributor<?> contributor =
      (WeightedSearchEverywhereContributor<?>)createClassContributor(getProject(), getTestRootDisposable(), null, false);

    int exactMatchDegree = DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE;
    assertTrue(weightOf(contributor, "foo.Bar", barClass) >= exactMatchDegree);
    assertTrue(weightOf(contributor, "foo.Ba", barClass) < exactMatchDegree);
  }

  public void test_a_class_is_not_an_exact_match_for_a_member_search_text() {
    PsiClass barClass = myFixture.addClass("package foo; class Bar { String baz; }");
    WeightedSearchEverywhereContributor<?> contributor =
      (WeightedSearchEverywhereContributor<?>)createClassContributor(getProject(), getTestRootDisposable(), null, false);

    // The Classes tab drops "#baz" from the search text, because it navigates to the member after
    // it opens the class. The class alone does not answer the whole search text, so it must not
    // get the exact match degree.
    assertTrue(weightOf(contributor, "foo.Bar#baz", barClass) < DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE);
    // The same holds for an anonymous class search text.
    assertTrue(weightOf(contributor, "foo.Bar$1", barClass) < DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE);
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

  /** @return the weight of each copy of the item, in the order of the search */
  private static List<Integer> weightsOf(WeightedSearchEverywhereContributor<?> contributor, String text, Object item) {
    List<?> items = contributor.searchWeightedElements(text, new MockProgressIndicator(), ELEMENTS_LIMIT).getItems();
    List<Integer> weights = new ArrayList<>();
    for (Object each : items) {
      FoundItemDescriptor<?> descriptor = (FoundItemDescriptor<?>)each;
      if (item.equals(descriptor.getItem())) weights.add(descriptor.getWeight());
    }
    assertFalse("The search for '" + text + "' did not return " + item, weights.isEmpty());
    return weights;
  }

  private static int weightOf(WeightedSearchEverywhereContributor<?> contributor, String text, Object item) {
    return weightsOf(contributor, text, item).getFirst();
  }

  @SuppressWarnings("unchecked")
  private List<PsiClass> gotoClass(String text, boolean checkboxState, PsiElement context) {
    return (List<PsiClass>)getContributorElements(createClassContributor(getProject(), getTestRootDisposable(), context, checkboxState),
                                                  text);
  }

  private List<PsiClass> gotoClass(String text, boolean checkboxState) {
    return gotoClass(text, checkboxState, null);
  }

  private List<PsiClass> gotoClass(String text) {
    return gotoClass(text, false, null);
  }

  @SuppressWarnings("unchecked")
  private List<PsiElement> gotoSymbol(String text, boolean checkboxState, PsiElement context) {
    return (List<PsiElement>)getContributorElements(createSymbolContributor(getProject(), getTestRootDisposable(), context, checkboxState),
                                                    text);
  }

  private List<PsiElement> gotoSymbol(String text, boolean checkboxState) {
    return gotoSymbol(text, checkboxState, null);
  }

  private List<PsiElement> gotoSymbol(String text) {
    return gotoSymbol(text, false, null);
  }

  @SuppressWarnings("unchecked")
  private List<PsiFile> gotoFile(String text, boolean checkboxState, PsiElement context) {
    return (List<PsiFile>)getContributorElements(createFileContributor(getProject(), getTestRootDisposable(), context, checkboxState),
                                                 text);
  }

  private List<PsiFile> gotoFile(String text, boolean checkboxState) {
    return gotoFile(text, checkboxState, null);
  }

  private List<PsiFile> gotoFile(String text) {
    return gotoFile(text, false, null);
  }

  private static List<?> getContributorElements(SearchEverywhereContributor<?> contributor, String text) {
    return calcContributorElements(contributor, text);
  }

  public static List<?> calcContributorElements(SearchEverywhereContributor<?> contributor, String text) {
    return contributor.search(text, new MockProgressIndicator(), ELEMENTS_LIMIT).getItems();
  }

  /**
   * @return the found items, the best weight first.
   * The provider can report one item more than once, so the result keeps the best copy, as Search Everywhere does.
   */
  public static List<Object> calcWeightedContributorElements(WeightedSearchEverywhereContributor<?> contributor, String text) {
    List<?> items = contributor.searchWeightedElements(text, new MockProgressIndicator(), ELEMENTS_LIMIT).getItems();
    Map<Object, Integer> bestWeights = new LinkedHashMap<>();
    for (Object each : items) {
      FoundItemDescriptor<?> descriptor = (FoundItemDescriptor<?>)each;
      bestWeights.merge(descriptor.getItem(), descriptor.getWeight(), Math::max);
    }
    List<Map.Entry<Object, Integer>> sortedEntries =
      ContainerUtil.sorted(bestWeights.entrySet(), Comparator.comparingInt(e -> -e.getValue()));
    return ContainerUtil.map(sortedEntries, Map.Entry::getKey);
  }

  public static SearchEverywhereContributor<Object> createClassContributor(Project project,
                                                                           Disposable parentDisposable,
                                                                           PsiElement context,
                                                                           boolean everywhere) {
    TestClassContributor res = new TestClassContributor(createEvent(project, context));
    res.setEverywhere(everywhere);
    Disposer.register(parentDisposable, res);
    return res;
  }

  public static SearchEverywhereContributor<Object> createClassContributor(Project project,
                                                                           Disposable parentDisposable,
                                                                           PsiElement context) {
    return ChooseByNameTest.createClassContributor(project, parentDisposable, context, false);
  }

  public static SearchEverywhereContributor<Object> createClassContributor(Project project, Disposable parentDisposable) {
    return ChooseByNameTest.createClassContributor(project, parentDisposable, null, false);
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

  public static SearchEverywhereContributor<Object> createFileContributor(Project project,
                                                                          Disposable parentDisposable,
                                                                          PsiElement context) {
    return ChooseByNameTest.createFileContributor(project, parentDisposable, context, false);
  }

  public static SearchEverywhereContributor<Object> createFileContributor(Project project, Disposable parentDisposable) {
    return ChooseByNameTest.createFileContributor(project, parentDisposable, null, false);
  }

  public static SearchEverywhereContributor<Object> createSymbolContributor(Project project,
                                                                            Disposable parentDisposable,
                                                                            PsiElement context,
                                                                            boolean everywhere) {
    TestSymbolContributor res = new TestSymbolContributor(createEvent(project, context));
    res.setEverywhere(everywhere);
    Disposer.register(parentDisposable, res);
    return res;
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

  private static class TestClassContributor extends ClassSearchEverywhereContributor {
    private TestClassContributor(@NotNull AnActionEvent event) {
      super(event);
    }

    public void setEverywhere(boolean state) {
      myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, state));
    }

    @NotNull
    @Override
    public String getSearchProviderId() {
      return "ClassSearchEverywhereContributor";
    }
  }

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

  private static class TestSymbolContributor extends SymbolSearchEverywhereContributor {
    private TestSymbolContributor(@NotNull AnActionEvent event) {
      super(event);
    }

    public void setEverywhere(boolean state) {
      myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, state));
    }

    @NotNull
    @Override
    public String getSearchProviderId() {
      return "SymbolSearchEverywhereContributor";
    }
  }
}
