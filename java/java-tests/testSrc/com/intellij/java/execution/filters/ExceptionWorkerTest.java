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
package com.intellij.java.execution.filters;

import com.intellij.execution.filters.ExceptionExFilterFactory;
import com.intellij.execution.filters.ExceptionInfoCache;
import com.intellij.execution.filters.ExceptionWorker;
import com.intellij.execution.filters.FilterMixin;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.ArrayList;

/**
 * @author gregsh
 */
public class ExceptionWorkerTest extends LightCodeInsightFixtureTestCase {

  public void testParsing() {
    myFixture.addClass("package com.sample;\n" +
                       "\n" +
                       "/**\n" +
                       " * Created with IntelliJ IDEA.\n" +
                       " * User: jetbrains\n" +
                       " * Date: 11/26/12\n" +
                       " * Time: 6:08 PM\n" +
                       " * To change this template use File | Settings | File Templates.\n" +
                       " */\n" +
                       "public class RunningMain {\n" +
                       "  public static void main(String[] args) throws Exception {\n" +
                       "    try {\n" +
                       "      func1();\n" +
                       "    }\n" +
                       "    finally {\n" +
                       "\n" +
                       "    }\n" +
                       "  }\n" +
                       "\n" +
                       "  static void func1() {\n" +
                       "    try {\n" +
                       "      func();\n" +
                       "    }\n" +
                       "    finally {\n" +
                       "\n" +
                       "    }\n" +
                       "  }\n" +
                       "\n" +
                       "  static void func() {\n" +
                       "    throw new NullPointerException();\n" +
                       "  }\n" +
                       "}\n");

    final String testData = "Exception in thread \"main\" java.lang.NullPointerException\n" +
                      "\tat com.sample.RunningMain.func(RunningMain.java:30)\n" +
                      "\tat com.sample.RunningMain.func1(RunningMain.java:22)\n" +
                      "\tat com.sample.RunningMain.main(RunningMain.java:13)\n";
    final Document document = EditorFactory.getInstance().createDocument(testData);
    FilterMixin filter = (FilterMixin)new ExceptionExFilterFactory().create(GlobalSearchScope.projectScope(getProject()));
    final ArrayList<String> result = new ArrayList<>();
    filter.applyHeavyFilter(document, 0, 0, r -> r.getResultItems().forEach(
      highlight -> result.add(new TextRange(highlight.getHighlightStartOffset(), highlight.getHighlightEndOffset()).substring(testData))));
    assertSameElements(result, "com.sample.RunningMain.func1", "com.sample.RunningMain.main");
  }

  public void testAnomalyParenthesisParsing() {
    assertParsed("at youtrack.jetbrains.com.Issue.IDEA_125137()(FooTest.groovy:2)\n", "youtrack.jetbrains.com.Issue", "IDEA_125137()", "FooTest.groovy", 2);
    assertParsed("at youtrack.jetbrains.com.Issue.IDEA_125137()Hmm(FooTest.groovy:2)\n", "youtrack.jetbrains.com.Issue", "IDEA_125137()Hmm", "FooTest.groovy", 2);
    assertParsed("p1.Cl.mee(p1.Cl.java:87) (A MESSAGE) IDEA-133794 (BUG START WITH 1)\n", "p1.Cl", "mee", "p1.Cl.java", 87);
  }

  private static void assertParsed(String line, String className, String methodName, String fileName, int lineIndex) {
    assertTrue(line.endsWith("\n"));
    ExceptionWorker.ParsedLine trinity = ExceptionWorker.parseExceptionLine(line);
    assertNotNull(trinity);
    assertEquals(className, trinity.classFqnRange.subSequence(line));
    assertEquals(methodName, trinity.methodNameRange.subSequence(line));
    assertEquals(fileName, trinity.fileName);
    assertEquals(lineIndex, trinity.lineNumber);
  }

  public void testYourKitFormat() {
    assertParsed("com.intellij.util.concurrency.Semaphore.waitFor(long) Semaphore.java:89\n",
                 "com.intellij.util.concurrency.Semaphore", "waitFor", "Semaphore.java", 89);
  }

  public void testForcedJstackFormat() {
    assertParsed(" - java.lang.ref.ReferenceQueue.remove(long) @bci=151, line=143 (Compiled frame)\n",
                 "java.lang.ref.ReferenceQueue", "remove", null, 143);

  }

  public void testJava9ModulePrefixed() {
    String line = "at mod.name/p.A.foo(A.java:2)\n";
    assertParsed(line, "p.A", "foo", "A.java", 2);

    PsiClass psiClass = myFixture.addClass("package p; public class A {\n" +
                                           "  public void foo() {}\n" +
                                           "}");
    ExceptionWorker worker = new ExceptionWorker(new ExceptionInfoCache(GlobalSearchScope.projectScope(getProject())));
    worker.execute(line, line.length());
    PsiClass aClass = worker.getPsiClass();
    assertNotNull(aClass);
    assertEquals(psiClass, aClass);
  }

  public void testNonClassInTheLine() {
    String line = "2016-12-20 10:58:36,617 [   5740]   INFO - llij.ide.plugins.PluginManager - Loaded bundled plugins: Android Support (10.2.2), Ant Support (1.0), Application Servers View (0.2.0), AspectJ Support (1.2), CFML Support (3.53), CSS Support (163.7743.44), CVS Integration (11), Cloud Foundry integration (1.0), CloudBees integration (1.0), Copyright (8.1), Coverage (163.7743.44), DSM Analysis (1.0.0), Database Tools and SQL (1.0), Eclipse Integration (3.0), EditorConfig (163.7743.44), Emma (163.7743.44), Flash/Flex Support (163.7743.44)";
    assertNull(ExceptionWorker.parseExceptionLine(line));
    assertNull(ExceptionWorker.parseExceptionLine(line + "\n"));
  }
}
