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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.SkipSlowTestLocally;

@SkipSlowTestLocally
public class CompilerReferencesFindUsagesTest extends DaemonAnalyzerTestCase {
  //TODO merge tests
  private boolean myDefaultEnableState;
  private CompilerTester myCompilerTester;

  @Override
  public void setUp() throws Exception {
    myDefaultEnableState = CompilerReferenceService.IS_ENABLED_KEY.asBoolean();
    CompilerReferenceService.IS_ENABLED_KEY.setValue(true);
    CompilerReferenceService.enabledInTests = true;
    super.setUp();
    myCompilerTester = new CompilerTester(myModule);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      CompilerReferenceService.IS_ENABLED_KEY.setValue(myDefaultEnableState);
      CompilerReferenceService.enabledInTests = false;
      myCompilerTester.tearDown();
    }
    finally {
      myCompilerTester = null;
      super.tearDown();
    }
  }

  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/compiler/compilerReferenceFindUsages/";
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
}
