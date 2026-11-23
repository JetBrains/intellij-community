// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightSubFixture;
import static com.intellij.psi.SmartPointersKt.createSmartPointer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SkipSlowTestLocally
@TestApplication
public class CompilerReferencesFindUsagesTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = heavyJavaCodeInsightSubFixture(
    "compilerReferencesFindUsagesFixture", null,
    context -> new DaemonAnalyzerFixture(context, getTestDataPath(), LocalInspectionTool.EMPTY_ARRAY) {
      @Override
      protected @NotNull Sdk getTestProjectJdk() {
        return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      }
    });

  //TODO merge tests
  private CompilerTester myCompilerTester;
  private String myName;

  @BeforeEach
  void setUp(TestInfo testInfo) throws Exception {
    myName = testInfo.getTestMethod().orElseThrow().getName();
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    myCompilerTester = new CompilerTester(fixture.getModule());
    IdeaTestUtil.setProjectLanguageLevel(fixture.getProject(), LanguageLevel.JDK_1_8);
  }

  @AfterEach
  void tearDown() {
    if (myCompilerTester != null) {
      myCompilerTester.tearDown();
      myCompilerTester = null;
    }
  }

  private static @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/compiler/compilerReferenceFindUsages/";
  }

  private PsiClass findLibraryClass(String qualifiedName) {
    return ReadAction.computeBlocking(() -> daemonFixture.get().getJavaFacade().findClass(qualifiedName));
  }

  private static PsiMethod[] findMethodsByName(PsiClass aClass, String name) {
    return ReadAction.computeBlocking(() -> aClass.findMethodsByName(name, false));
  }

  @Test
  public void testMethodUsageOnGetter() {
    daemonFixture.get().configureByFiles(myName, myName + "/Foo.java", myName + "/FooFactory.java", myName + "/Bar.java");
    PsiMethod methodToSearch = findMethodsByName(daemonFixture.get().findClass("Foo"), "someMethod")[0];
    assertSameUsageAfterRebuild(methodToSearch);
  }

  @Test
  public void testMethodUsageInClassHierarchy() {
    daemonFixture.get().configureByFiles(myName, myName + "/Bar.java", myName + "/Foo.java");
    PsiMethod methodToSearch = findMethodsByName(daemonFixture.get().findClass("Foo"), "someMethod")[0];
    assertSameUsageAfterRebuild(methodToSearch);
  }

  @Test
  public void testMethodUsageInClassHierarchy2() {
    daemonFixture.get().configureByFiles(myName, myName + "/Bar.java", myName + "/Foo.java");
    PsiMethod methodToSearch = findMethodsByName(findLibraryClass(CommonClassNames.JAVA_LANG_RUNNABLE), "run")[0];
    assertSameUsageAfterRebuild(methodToSearch);
  }

  @Test
  public void testLibMethodUsage() {
    daemonFixture.get().configureByFile(myName + "/Foo.java");
    myCompilerTester.rebuild();
    PsiMethod methodToSearch = findMethodsByName(findLibraryClass(CommonClassNames.JAVA_UTIL_COLLECTIONS), "emptyList")[0];
    assertOneElement(searchReferences(methodToSearch));
  }

  @Test
  public void testLibClassUsage() {
    daemonFixture.get().configureByFile(myName + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = findLibraryClass("java.lang.System");
    assertOneElement(searchReferences(classForSearch));
  }

  @Test
  public void testLibClassInJavaDocUsage() {
    daemonFixture.get().configureByFile(myName + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = findLibraryClass("java.lang.System");
    assertOneElement(searchReferences(classForSearch));
  }

  @Test
  public void testCompileTimeConstFindUsages() {
    daemonFixture.get().configureByFiles(myName, myName + "/Bar.java", myName + "/Foo.java");
    PsiClass foo = daemonFixture.get().findClass("Foo");
    PsiField classForSearch = ReadAction.computeBlocking(() -> foo.findFieldByName("CONST", false));
    assertSameUsageAfterRebuild(classForSearch);
  }

  private void assertSameUsageAfterRebuild(PsiElement target) {
    PsiReference ref1 = assertOneElement(searchReferences(target));

    SmartPsiElementPointer<PsiElement> pRef = ReadAction.computeBlocking(() -> createSmartPointer(ref1.getElement()));
    SmartPsiElementPointer<PsiElement> pTarget = ReadAction.computeBlocking(() -> createSmartPointer(target));
    myCompilerTester.rebuild();

    PsiReference ref2 = assertOneElement(searchReferences(ReadAction.computeBlocking(() -> pTarget.getElement())));
    ReadAction.runBlocking(() -> assertEquals(pRef.getElement(), ref2.getElement()));
  }

  @Test
  public void testFindUsagesInInjectedCode() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    new MyTestInjector(PsiManager.getInstance(daemonFixture.get().getProject())).injectAll(fixture.getTestRootDisposable());
    fixture.configureByFile(myName + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = findLibraryClass("java.lang.System");
    PsiReference reference = assertOneElement(searchReferences(classForSearch));
    assertTrue(ReadAction.computeBlocking(
      () -> InjectedLanguageManager.getInstance(daemonFixture.get().getProject()).isInjectedFragment(reference.getElement().getContainingFile())));
  }

  @Test
  public void testFindUsagesWithExcludedFromCompilationDirectory() {
    doTestRunnableFindUsagesWithExcludesConfiguration(configuration -> {
      final VirtualFile dirToExclude = ReadAction.computeBlocking(() -> daemonFixture.get().findClass("A").getContainingFile().getVirtualFile().getParent());
      configuration.addExcludeEntryDescription(new ExcludeEntryDescription(dirToExclude, false, false, daemonFixture.get().getProject()));
    }, 3, "Foo.java", "excluded/A.java", "excluded/child/ShouldFindHere.java");
  }

  @Test
  public void testFindUsagesWithRecursivelyExcludedFromCompilationDirectory() {
    doTestRunnableFindUsagesWithExcludesConfiguration(configuration -> {
      final VirtualFile dirToExclude =
        ReadAction.computeBlocking(() -> daemonFixture.get().findClass("ShouldFindHere").getContainingFile().getVirtualFile().getParent().getParent());
      configuration.addExcludeEntryDescription(new ExcludeEntryDescription(dirToExclude, true, false, daemonFixture.get().getProject()));
    }, 2, "Foo.java", "excluded/child/ShouldFindHere.java");
  }

  @Test
  public void testFindUsagesWithExcludedFromCompilationFile() {
    doTestRunnableFindUsagesWithExcludesConfiguration(configuration -> {
      final VirtualFile dirToExclude = ReadAction.computeBlocking(() -> daemonFixture.get().findClass("Foo").getContainingFile().getVirtualFile());
      configuration.addExcludeEntryDescription(new ExcludeEntryDescription(dirToExclude, false, true, daemonFixture.get().getProject()));
    }, 2, "Foo.java", "Bar.java");
  }

  @Test
  public void testOverloadedMethods() {
    daemonFixture.get().configureByFiles(myName, myName + "/Foo.java", myName + "/A.java", myName + "/B.java");
    Arrays.stream(findMethodsByName(daemonFixture.get().findClass("Foo"), "bar")).forEach((m) -> assertEquals(2, searchReferences(m).size()));
    myCompilerTester.rebuild();
    Arrays.stream(findMethodsByName(daemonFixture.get().findClass("Foo"), "bar")).forEach((m) -> assertEquals(2, searchReferences(m).size()));
  }

  @Test
  public void testImplicitToStringSearch() {
    daemonFixture.get().configureByFiles(myName, myName + "/Foo.java", myName + "/A.java", myName + "/B.java");
    Arrays.stream(findMethodsByName(daemonFixture.get().findClass("FooImpl"), "toString")).forEach((m) -> assertEquals(2, searchUsages(m)));
    Arrays.stream(findMethodsByName(daemonFixture.get().findClass("Foo"), "toString")).forEach((m) -> assertEquals(2, searchUsages(m)));
    Arrays.stream(findMethodsByName(findLibraryClass(CommonClassNames.JAVA_LANG_OBJECT), "toString")).forEach((m) -> assertEquals(2, searchUsages(m)));
    myCompilerTester.rebuild();
    Arrays.stream(findMethodsByName(daemonFixture.get().findClass("FooImpl"), "toString")).forEach((m) -> assertEquals(2, searchUsages(m)));
    Arrays.stream(findMethodsByName(daemonFixture.get().findClass("Foo"), "toString")).forEach((m) -> assertEquals(2, searchUsages(m)));
    Arrays.stream(findMethodsByName(findLibraryClass(CommonClassNames.JAVA_LANG_OBJECT), "toString")).forEach((m) -> assertEquals(2, searchUsages(m)));
  }

  private void doTestRunnableFindUsagesWithExcludesConfiguration(@NotNull Consumer<ExcludesConfiguration> excludesConfigurationPatcher,
                                                                 int expectedUsagesCount,
                                                                 String... testFiles) {
    final ExcludesConfiguration excludesConfiguration = CompilerConfiguration.getInstance(daemonFixture.get().getProject()).getExcludedEntriesConfiguration();
    try {
      daemonFixture.get().configureByFiles(myName, Arrays.stream(testFiles).map(f -> myName + "/" + f).toArray(String[]::new));
      excludesConfigurationPatcher.consume(excludesConfiguration);
      assertEquals(expectedUsagesCount, ReadAction.computeBlocking(
        () -> FunctionalExpressionSearch.search(daemonFixture.get().getJavaFacade().findClass(CommonClassNames.JAVA_LANG_RUNNABLE)).findAll()).size());
      myCompilerTester.rebuild();
      assertEquals(expectedUsagesCount, ReadAction.computeBlocking(
        () -> FunctionalExpressionSearch.search(daemonFixture.get().getJavaFacade().findClass(CommonClassNames.JAVA_LANG_RUNNABLE)).findAll()).size());
    } finally {
      excludesConfiguration.removeAllExcludeEntryDescriptions();
    }
  }

  private int searchUsages(@NotNull PsiMethod method) {
    JavaFindUsagesHandlerFactory factory = JavaFindUsagesHandlerFactory.getInstance(daemonFixture.get().getProject());
    int[] count = {0};
    factory.createFindUsagesHandler(method, false).processElementUsages(method, info -> {
      count[0]++;
      return true;
    }, factory.getFindMethodOptions());
    return count[0];
  }

  private Collection<PsiReference> searchReferences(@NotNull PsiElement element) {
    if (element instanceof PsiMethod) {
      return ReadAction.computeBlocking(
        () -> MethodReferencesSearch.search((PsiMethod)element, GlobalSearchScope.projectScope(daemonFixture.get().getProject()), false).findAll());
    }
    return ReadAction.computeBlocking(() -> ReferencesSearch.search(element, GlobalSearchScope.projectScope(daemonFixture.get().getProject())).findAll());
  }

  private static <T> T assertOneElement(Collection<T> collection) {
    assertEquals(1, collection.size(), collection::toString);
    return collection.iterator().next();
  }
}
