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
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaOverrideMethodsSearchTest extends LightCodeInsightFixtureTestCase {
  public void testSearchInLocalScopePerformance() throws Exception {
    myFixture.addClass("public interface I {void m();}");
    for (int i = 0; i < 1000; i++) {
      final int dirIdx = i % 100;
      myFixture.addFileToProject("/a" + dirIdx + "/Foo" + i + ".java", "class Foo" + i + " implements I {public void m() {}}");
    }
    myFixture.configureByText("a.java", "class Foo implements I {public void m<caret>(){}}");
    final PsiMethod method = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethod.class);
    assertNotNull(method);
    final PsiMethod superMethod = method.findDeepestSuperMethods()[0];
    PlatformTestUtil.startPerformanceTest("search in local scope", 100, () -> {
      final Collection<PsiMethod> all = OverridingMethodsSearch.search(superMethod, new LocalSearchScope(getFile()), true).findAll();
      assertTrue(all.size() == 1);
    }).useLegacyScaling().attempts(1).assertTiming();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
