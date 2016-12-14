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

package com.intellij.navigation;

import com.intellij.execution.filters.ExceptionInfoCache;
import com.intellij.execution.filters.ExceptionWorker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class ExceptionFilterTest extends JavaCodeInsightFixtureTestCase {

  public void testJava9ModulePrefixed() throws Throwable {
    PsiClass psiClass = myFixture.addClass("package p; public class A {\n" +
                                          "  public void foo() {}\n" +
                                          "}");
    ExceptionWorker worker = new ExceptionWorker(new ExceptionInfoCache(GlobalSearchScope.projectScope(getProject())));
    String line = "at mod.name/p.A.foo(A.java:2)";
    worker.execute(line, line.length());
    PsiClass aClass = worker.getPsiClass();
    assertNotNull(aClass);
    assertEquals(psiClass, aClass);
  }
}