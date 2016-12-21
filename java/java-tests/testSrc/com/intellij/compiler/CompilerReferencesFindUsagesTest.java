/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

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

  public void testMethodUsageOnGetter() throws Exception {
    configureByFiles(getName(), getName() + "/Foo.java", getName() + "/FooFactory.java", getName() + "/Bar.java");
    PsiMethod methodToSearch = findClass("Foo").findMethodsByName("someMethod", false)[0];
    assertOneElement(MethodReferencesSearch.search(methodToSearch).findAll());
    myCompilerTester.rebuild();
    assertOneElement(MethodReferencesSearch.search(methodToSearch).findAll());
  }

  public void testMethodUsageInClassHierarchy() throws Exception {
    configureByFiles(getName(), getName() + "/Bar.java", getName() + "/Foo.java");
    PsiMethod methodToSearch = findClass("Foo").findMethodsByName("someMethod", false)[0];
    assertOneElement(MethodReferencesSearch.search(methodToSearch).findAll());
    myCompilerTester.rebuild();
    assertOneElement(MethodReferencesSearch.search(methodToSearch).findAll());
  }

  public void testMethodUsageInClassHierarchy2() throws Exception {
    configureByFiles(getName(), getName() + "/Bar.java", getName() + "/Foo.java");
    PsiMethod methodToSearch = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_RUNNABLE).findMethodsByName("run", false)[0];
    assertOneElement(MethodReferencesSearch.search(methodToSearch).findAll());
    myCompilerTester.rebuild();
    assertOneElement(MethodReferencesSearch.search(methodToSearch).findAll());
  }

  public void testLibMethodUsage() throws Exception {
    configureByFile(getName() + "/Foo.java");
    myCompilerTester.rebuild();
    PsiMethod methodToSearch = myJavaFacade.findClass(CommonClassNames.JAVA_UTIL_COLLECTIONS).findMethodsByName("emptyList", false)[0];
    assertOneElement(MethodReferencesSearch.search(methodToSearch).findAll());
  }

  public void testLibClassUsage() throws Exception {
    configureByFile(getName() + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = myJavaFacade.findClass("java.lang.System");
    assertOneElement(ReferencesSearch.search(classForSearch).findAll());
  }

  public void testLibClassInJavaDocUsage() throws Exception {
    configureByFile(getName() + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = myJavaFacade.findClass("java.lang.System");
    assertOneElement(ReferencesSearch.search(classForSearch).findAll());
  }

  public void testCompileTimeConstFindUsages() throws Exception {
    configureByFiles(getName(), getName() + "/Bar.java", getName() + "/Foo.java");
    PsiField classForSearch = findClass("Foo").findFieldByName("CONST", false);
    PsiElement referenceBefore = assertOneElement(ReferencesSearch.search(classForSearch).findAll()).getElement();
    myCompilerTester.rebuild();
    PsiElement referenceAfter = assertOneElement(ReferencesSearch.search(classForSearch).findAll()).getElement();
    assertTrue(referenceBefore == referenceAfter);
  }

  public void testFindUsagesInInjectedCode() throws Exception {
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
    configureByFile(getName() + "/Foo.java");
    myCompilerTester.rebuild();
    PsiClass classForSearch = myJavaFacade.findClass("java.lang.System");
    PsiReference reference = assertOneElement(ReferencesSearch.search(classForSearch).findAll());
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

  public void testOverloadedMethods() throws Exception {
    configureByFiles(getName(), getName() + "/Foo.java", getName() + "/A.java", getName() + "/B.java");
    PsiMethod[] methodsToSearch = findClass("Foo").findMethodsByName("bar", false);
    Arrays.stream(methodsToSearch).forEach((m) -> assertSize(2, MethodReferencesSearch.search(m, false).findAll()));
    myCompilerTester.rebuild();
    Arrays.stream(methodsToSearch).forEach((m) -> assertSize(2, MethodReferencesSearch.search(m, false).findAll()));
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
}
