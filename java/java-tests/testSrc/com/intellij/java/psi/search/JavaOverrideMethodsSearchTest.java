/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.search;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class JavaOverrideMethodsSearchTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSearchHonorsLocalScope() {
    myFixture.addClass("package pkg; public interface I {void m();}");

    PsiFileImpl anotherFile = (PsiFileImpl)myFixture.addFileToProject("pkg2/Another.java", "class Another implements pkg.I {public void m() {}}");

    myFixture.configureByText("a.java", "class Foo implements pkg.I {public void m<caret>(){}}");

    PsiMethod method = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethod.class);
    PsiMethod superMethod = method.findDeepestSuperMethods()[0];

    assertNull(anotherFile.derefStub());
    assertFalse(anotherFile.isContentsLoaded());

    assertOneElement(OverridingMethodsSearch.search(superMethod, new LocalSearchScope(getFile()), true).findAll());

    // check we didn't go to another file during search
    assertNull(anotherFile.derefStub());
    assertFalse(anotherFile.isContentsLoaded());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
