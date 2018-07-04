// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.compiler;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PointersKt;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

@SkipSlowTestLocally
public class CompilerReferencesFindUsagesTest extends DaemonAnalyzerTestCase {
  //TODO merge tests
  private boolean myDefaultEnableState;
  private CompilerTester myCompilerTester;

  @Override
  public void setUp() throws Exception {
    myDefaultEnableState = CompilerReferenceService.IS_ENABLED_KEY.asBoolean();
    CompilerReferenceService.IS_ENABLED_KEY.setValue(true);
    super.setUp();
    myCompilerTester = new CompilerTester(myModule);
    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      CompilerReferenceService.IS_ENABLED_KEY.setValue(myDefaultEnableState);
      myCompilerTester.tearDown();
    }
    finally {
      myCompilerTester = null;
      super.tearDown();
    }
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/compiler/compilerReferenceFindUsages/";
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }

  public void testMethodUsageOnGetter() {
    configureByFiles(getName(), getName() + "/Foo.java", getName() + "/FooFactory.java", getName() + "/Bar.java");
    PsiMethod methodToSearch = findClass("Foo").findMethodsByName("someMethod", false)[0];
    assertSameUsageAfterRebuild(methodToSearch);
  }

  public void testMethodUsageInClassHierarchy() {
    configureByFiles(getName(), getName() + "/Bar.java", getName() + "/Foo.java");
    PsiMethod methodToSearch = findClass("Foo").findMethodsByName("someMethod", false)[0];
    assertSameUsageAfterRebuild(methodToSearch);
  }

  public void testMethodUsageInClassHierarchy2() {
    configureByFiles(getName(), getName() + "/Bar.java", getName() + "/Foo.java");
    PsiMethod methodToSearch = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_RUNNABLE).findMethodsByName("run", false)[0];
    assertSameUsageAfterRebuild(methodToSearch);
  }

  public void testLibMethodUsage() throws Exception {
    configureByFile(getName() + "/Foo.java");
    myCompilerTester.rebuild();
    PsiMethod methodToSearch = myJavaFacade.findClass(CommonClassNames.JAVA_UTIL_COLLECTIONS).findMethodsByName("emptyList", false)[0];
    assertOneElement(searchReferences(methodToSearch));
  }

  public void testLibClassUsage() throws Exception {
    configureByFile(getName() + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = myJavaFacade.findClass("java.lang.System");
    assertOneElement(searchReferences(classForSearch));
  }

  public void testLibClassInJavaDocUsage() throws Exception {
    configureByFile(getName() + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = myJavaFacade.findClass("java.lang.System");
    assertOneElement(searchReferences(classForSearch));
  }

  public void testCompileTimeConstFindUsages() {
    configureByFiles(getName(), getName() + "/Bar.java", getName() + "/Foo.java");
    PsiField classForSearch = findClass("Foo").findFieldByName("CONST", false);
    assertSameUsageAfterRebuild(classForSearch);
  }

  private void assertSameUsageAfterRebuild(PsiElement target) {
    PsiReference ref1 = assertOneElement(searchReferences(target));

    SmartPsiElementPointer<PsiElement> pRef = PointersKt.createSmartPointer(ref1.getElement());
    SmartPsiElementPointer<PsiElement> pTarget = PointersKt.createSmartPointer(target);
    myCompilerTester.rebuild();

    PsiReference ref2 = assertOneElement(searchReferences(pTarget.getElement()));
    assertEquals(pRef.getElement(), ref2.getElement());
  }

  public void testFindUsagesInInjectedCode() throws Exception {
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
    configureByFile(getName() + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = myJavaFacade.findClass("java.lang.System");
    PsiReference reference = assertOneElement(searchReferences(classForSearch));
    assertTrue(InjectedLanguageManager.getInstance(getProject()).isInjectedFragment(reference.getElement().getContainingFile()));
  }

  public void testFindUsagesWithExcludedFromCompilationDirectory() {
    doTestRunnableFindUsagesWithExcludesConfiguration(configuration -> {
      final VirtualFile dirToExclude = findClass("A").getContainingFile().getVirtualFile().getParent();
      configuration.addExcludeEntryDescription(new ExcludeEntryDescription(dirToExclude, false, false, myProject));
    }, 3, "Foo.java", "excluded/A.java", "excluded/child/ShouldFindHere.java");
  }

  public void testFindUsagesWithRecursivelyExcludedFromCompilationDirectory() {
    doTestRunnableFindUsagesWithExcludesConfiguration(configuration -> {
      final VirtualFile dirToExclude = findClass("ShouldFindHere").getContainingFile().getVirtualFile().getParent().getParent();
      configuration.addExcludeEntryDescription(new ExcludeEntryDescription(dirToExclude, true, false, myProject));
    }, 2, "Foo.java", "excluded/child/ShouldFindHere.java");
  }

  public void testFindUsagesWithExcludedFromCompilationFile() {
    doTestRunnableFindUsagesWithExcludesConfiguration(configuration -> {
      final VirtualFile dirToExclude = findClass("Foo").getContainingFile().getVirtualFile();
      configuration.addExcludeEntryDescription(new ExcludeEntryDescription(dirToExclude, false, true, myProject));
    }, 2, "Foo.java", "Bar.java");
  }

  public void testOverloadedMethods() {
    configureByFiles(getName(), getName() + "/Foo.java", getName() + "/A.java", getName() + "/B.java");
    Arrays.stream(findClass("Foo").findMethodsByName("bar", false)).forEach((m) -> assertSize(2, searchReferences(m)));
    myCompilerTester.rebuild();
    Arrays.stream(findClass("Foo").findMethodsByName("bar", false)).forEach((m) -> assertSize(2, searchReferences(m)));
  }

  public void testImplicitToStringSearch() {
    configureByFiles(getName(), getName() + "/Foo.java", getName() + "/A.java", getName() + "/B.java");
    Arrays.stream(findClass("FooImpl").findMethodsByName("toString", false)).forEach((m) -> assertEquals(2, searchUsages(m)));
    Arrays.stream(findClass("Foo").findMethodsByName("toString", false)).forEach((m) -> assertEquals(2, searchUsages(m)));
    Arrays.stream(myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT).findMethodsByName("toString", false)).forEach((m) -> assertEquals(2, searchUsages(m)));
    myCompilerTester.rebuild();
    Arrays.stream(findClass("FooImpl").findMethodsByName("toString", false)).forEach((m) -> assertEquals(2, searchUsages(m)));
    Arrays.stream(findClass("Foo").findMethodsByName("toString", false)).forEach((m) -> assertEquals(2, searchUsages(m)));
    Arrays.stream(myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT).findMethodsByName("toString", false)).forEach((m) -> assertEquals(2, searchUsages(m)));
  }

  private void doTestRunnableFindUsagesWithExcludesConfiguration(@NotNull Consumer<ExcludesConfiguration> excludesConfigurationPatcher,
                                                                 int expectedUsagesCount,
                                                                 String... testFiles) {
    final ExcludesConfiguration excludesConfiguration = CompilerConfiguration.getInstance(myProject).getExcludedEntriesConfiguration();
    try {
      configureByFiles(getName(), Arrays.stream(testFiles).map(f -> getName() + "/" + f).toArray(String[]::new));
      excludesConfigurationPatcher.consume(excludesConfiguration);
      assertSize(expectedUsagesCount, FunctionalExpressionSearch.search(myJavaFacade.findClass(CommonClassNames.JAVA_LANG_RUNNABLE)).findAll());
      myCompilerTester.rebuild();
      assertSize(expectedUsagesCount, FunctionalExpressionSearch.search(myJavaFacade.findClass(CommonClassNames.JAVA_LANG_RUNNABLE)).findAll());
    } finally {
      excludesConfiguration.removeAllExcludeEntryDescriptions();
    }
  }

  private int searchUsages(@NotNull PsiMethod method) {
    JavaFindUsagesHandlerFactory factory = JavaFindUsagesHandlerFactory.getInstance(getProject());
    int[] count = {0};
    factory.createFindUsagesHandler(method, false).processElementUsages(method, info -> {
      count[0]++;
      return true;
    }, factory.getFindMethodOptions());
    return count[0];
  }

  private Collection<PsiReference> searchReferences(@NotNull PsiElement element) {
    if (element instanceof PsiMethod) {
      return MethodReferencesSearch.search((PsiMethod)element, GlobalSearchScope.projectScope(getProject()), false).findAll();
    }
    return ReferencesSearch.search(element, GlobalSearchScope.projectScope(getProject())).findAll();
  }
}
