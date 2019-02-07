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

import com.intellij.execution.filters.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

  public void testColumnFinder() {
    @Language("JAVA") String classText =
      "/** @noinspection ALL*/\n" +
      "class SomeClass {\n" +
      "  SomeClass() {\n" +
      "    System.out.println((new int[0])[1]);\n" +
      "  }\n" +
      "  static class Inner implements Runnable {\n" +
      "    int test = 4;\n" +
      "    public void run() {\n" +
      "      try {\n" +
      "        System.out.println(test + test() + SomeClass.test());\n" +
      "      } catch(Exception ex) {\n" +
      "        throw new RuntimeException(ex);\n" +
      "      }\n" +
      "    }\n" +
      "    int test() { return 0; }\n" +
      "  }\n" +
      "  private static int test() {\n" +
      "    new SomeClass() {};\n" +
      "    return 1;\n" +
      "  }\n" +
      "  public static void main(String[] args) {\n" +
      "    class X {\n" +
      "      public void run() {\n" +
      "        new Runnable() {\n" +
      "          public void run() {\n" +
      "            Runnable inner = new Inner();\n" +
      "            inner.run();X.this.run();\n" +
      "          }\n" +
      "        }.run();\n" +
      "      }\n" +
      "    }\n" +
      "    new X().run();\n" +
      "  }\n" +
      "}";
    myFixture.configureByText("SomeClass.java", classText);
    Editor editor = myFixture.getEditor();
    assertEquals(classText, editor.getDocument().getText());
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.RuntimeException: java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0\n", null, null),
      Trinity.create("\tat SomeClass$Inner.run(SomeClass.java:12)\n", 12, 9),
      Trinity.create("\tat SomeClass$1X$1.run(SomeClass.java:27)\n", 27, 19),
      Trinity.create("\tat SomeClass$1X.run(SomeClass.java:29)\n", 29, 11),
      Trinity.create("\tat SomeClass.main(SomeClass.java:32)\n", 32, 13),
      Trinity.create("Caused by: java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0\n", null, null),
      Trinity.create("\tat SomeClass.<init>(SomeClass.java:4)\n", 4, 36),
      Trinity.create("\tat SomeClass$1.<init>(SomeClass.java:18)\n", 18, 9),
      Trinity.create("\tat SomeClass.test(SomeClass.java:18)\n", 18, 9),
      Trinity.create("\tat SomeClass$Inner.run(SomeClass.java:10)\n", 10, 54));
    ExceptionFilter filter = new ExceptionFilter(myFixture.getFile().getResolveScope());
    for (Trinity<String, Integer, Integer> line : traceAndPositions) {
      String stackLine = line.getFirst();
      Filter.Result result = filter.applyFilter(stackLine, stackLine.length());
      Integer row = line.getSecond();
      Integer column = line.getThird();
      if (row == null) {
        assertNull(result);
      }
      else {
        HyperlinkInfo info = result.getFirstHyperlinkInfo();
        assertNotNull(info);
        info.navigate(getProject());
        LogicalPosition actualPos = editor.getCaretModel().getLogicalPosition();
        assertEquals(new LogicalPosition(row - 1, column - 1), actualPos);
      }
    }
  }
}
