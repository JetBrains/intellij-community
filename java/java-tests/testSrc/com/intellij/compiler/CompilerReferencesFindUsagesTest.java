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
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.SkipSlowTestLocally;

@SkipSlowTestLocally
public class CompilerReferencesFindUsagesTest extends CompilerReferencesTestBase {
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/compiler/compilerReferenceFindUsages/";
  }

  public void testFindUsagesInInjectedCode() {
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
    myFixture.configureByFile(getName() + "/Foo.java");
    rebuildProject();
    PsiClass classForSearch = myFixture.getJavaFacade().findClass("java.lang.System");
    PsiReference reference = assertOneElement(ReferencesSearch.search(classForSearch).findAll());
    assertTrue(InjectedLanguageManager.getInstance(getProject()).isInjectedFragment(((PsiReferenceExpressionImpl)reference).getContainingFile()));
  }
}
