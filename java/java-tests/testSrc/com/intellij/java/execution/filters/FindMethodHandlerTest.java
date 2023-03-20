// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.filters;

import com.intellij.execution.filters.ExceptionFilter;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Trinity;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class FindMethodHandlerTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testColumnFinder() {
    @Language("JAVA") String classText =
      """
        //special line
        //special line
        //special line
        //special line
        //special line
        //special line
        //special line
        //special line
        //special line
        //special line
        /** @noinspection ALL*/
        class SomeClass {
          SomeClass() {
            System.out.println((new int[0])[1]);
          }
          static class Inner implements Runnable {
            int test = 4;
            public void run() {
              try {
                System.out.println(test + test() + SomeClass.test());
              } catch(Exception ex) {
                throw new RuntimeException(ex);
              }
            }
            int test() { return 0; }
          }
          private static int test() {
            new SomeClass() {};
            return 1;
          }
          public static void main(String[] args) {
            class X {
              public void run() {
                new Runnable() {
                  public void run() {
                    Runnable inner = new Inner();
                    inner.run();X.this.run();
                  }
                }.run();
              }
            }
            Runnable r = () -> new X().run();
            r.run();
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create(
        "Exception in thread \"main\" java.lang.RuntimeException: java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0\n",
        null, null),
      Trinity.create("\tat SomeClass$Inner.run(SomeClass.java:12)\n", 12 + 10, 15),
      Trinity.create("\tat SomeClass$1X$1.run(SomeClass.java:27)\n", null, null),
      Trinity.create("\tat SomeClass$1X.run(SomeClass.java:29)\n", null, null),
      Trinity.create("\tat SomeClass.lambda$main$0(SomeClass.java:32)\n", 32 + 10, 32),
      Trinity.create("\tat SomeClass.main(SomeClass.java:33)\n", 33 + 10, 7),
      Trinity.create("Caused by: java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0\n", null, null),
      Trinity.create("\tat SomeClass.<init>(SomeClass.java:4)\n", 4 + 10, 37),
      Trinity.create("\tat SomeClass$1.<init>(SomeClass.java:18)\n", null, null),
      Trinity.create("\tat SomeClass.test(SomeClass.java:18)\n", 18 + 10, 9),
      Trinity.create("\tat SomeClass.access$000(SomeClass.java:2)\n", null, null),
      Trinity.create("\tat SomeClass$Inner.run(SomeClass.java:10)\n", 10 + 10, 54));
    checkFindMethodHandler(classText, traceAndPositions);
  }

  public void testColumnFinderAssert() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        //special line
        //special line
        //special line
        //special line
        //special line
        //special line
        public class SomeClass {
          public static void main(String[] args) {
            assert false;
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.AssertionError\n", null, null),
      Trinity.create("\tat SomeClass.main(SomeClass.java:4)\n", 4 + 6, 5));
    checkFindMethodHandler(classText, traceAndPositions);
  }

  public void testColumnFinderArrayStore() {
    @Language("JAVA") String classText =
      """
        //special line
        //special line
        //special line
        //special line
        //special line
        //special line
        /** @noinspection ALL*/
        public class SomeClass {
          public static void main(String[] args) {
            Object[] arr = new String[1];
            arr[0] = 1;
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ArrayStoreException: java.lang.Integer\n", null, null),
      Trinity.create("\tat SomeClass.main(SomeClass.java:5)\n", 5 + 6, 12),
      Trinity.create("\tat SomeClass.unknown(SomeClass.java:0)\n", null, null),
      Trinity.create("\tat SomeClass.unknown(SomeClass.java:1)\n", null, null));
    checkFindMethodHandler(classText, traceAndPositions);
  }

  private void checkFindMethodHandler(String classText, List<Trinity<String, Integer, Integer>> traceAndPositions) {
    myFixture.configureByText("SomeClass.java", classText);
    Editor editor = myFixture.getEditor();
    assertEquals(classText, editor.getDocument().getText());
    ExceptionFilter filter = new ExceptionFilter(myFixture.getFile().getResolveScope());
    for (Trinity<String, Integer, Integer> line : traceAndPositions) {
      String stackLine = line.getFirst();
      Filter.Result result = filter.applyFilter(stackLine, stackLine.length());
      Integer row = line.getSecond();
      Integer column = line.getThird();
      if (column != null && row != null) {
        assertNotNull(result);
        HyperlinkInfo info = result.getFirstHyperlinkInfo();
        assertNotNull(info);
        info.navigate(getProject());
        LogicalPosition actualPos = editor.getCaretModel().getLogicalPosition();
        assertEquals(new LogicalPosition(row - 1, column - 1), actualPos);
      }
    }
  }
}
