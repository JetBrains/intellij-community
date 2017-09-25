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

package com.intellij.java.navigation;

import com.intellij.execution.filters.ExceptionInfoCache;
import com.intellij.execution.filters.ExceptionWorker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class ExceptionFilterTest extends JavaCodeInsightFixtureTestCase {

  public void testJava9ModulePrefixed() {
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

  public void testNonClassInTheLine() {
    ExceptionWorker worker = new ExceptionWorker(new ExceptionInfoCache(GlobalSearchScope.allScope(getProject())));
    String line = "2016-12-20 10:58:36,617 [   5740]   INFO - llij.ide.plugins.PluginManager - Loaded bundled plugins: Android Support (10.2.2), Ant Support (1.0), Application Servers View (0.2.0), AspectJ Support (1.2), CFML Support (3.53), CSS Support (163.7743.44), CVS Integration (11), Cloud Foundry integration (1.0), CloudBees integration (1.0), Copyright (8.1), Coverage (163.7743.44), DSM Analysis (1.0.0), Database Tools and SQL (1.0), Eclipse Integration (3.0), EditorConfig (163.7743.44), Emma (163.7743.44), Flash/Flex Support (163.7743.44)";
    worker.execute(line, line.length());
    assertNull(worker.getPsiClass());
  }
}