// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.filters;

import com.intellij.execution.filters.ExceptionFilter;
import com.intellij.execution.filters.ExceptionLineRefiner;
import com.intellij.execution.filters.ExceptionWorker;
import com.intellij.execution.filters.FindDivergedExceptionLineHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Trinity;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FindDivergedExceptionLineHandlerTest extends LightJavaCodeInsightFixtureTestCase {
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
      Trinity.create("\tat SomeClass$Inner.run(SomeClass.java:12)\n", null, null),
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
      Trinity.create("\tat SomeClass.main(SomeClass.java:6)\n", 5 + 6, 12),
      Trinity.create("\tat SomeClass.unknown(SomeClass.java:1)\n", null, null),
      Trinity.create("\tat SomeClass.unknown(SomeClass.java:2)\n", null, null));
    checkFindMethodHandler(classText, traceAndPositions);
  }
  public void testNonStaticField() {
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
            private final int x = 0;
            private final int y = staticT(x);
        
            private int staticT(int x) {
                return 1 / x;
            }
        
            public static void main(String[] args) {
                new SomeClass();
            }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n", null, null),
      Trinity.create("\tat SomeClass.staticT(SomeClass.java:6)\n", 6 + 7, 20),
      Trinity.create("\tat SomeClass.<init>(SomeClass.java:3)\n", 3 + 7, 27),
      Trinity.create("\tat SomeClass.main(SomeClass.java:10)\n", 10 + 7, 13));
    checkFindMethodHandler(classText, traceAndPositions);
  }

  public void testStaticField() {
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
            private static final int x = 0;
            private static final int y = staticT(x);
        
            private static int staticT(int x) {
                return 1 / x;
            }
        
            public static void main(String[] args) {
                new SomeClass();
            }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Caused by: java.lang.ArithmeticException: / by zero\n", null, null),
      Trinity.create("\tat SomeClass.staticT(SomeClass.java:6)\n", 6 + 7, 20),
      Trinity.create("\tat SomeClass.<clinit>(SomeClass.java:3)\n", 3 + 7, 34));
    checkFindMethodHandler(classText, traceAndPositions);
  }

  public void testStaticBlock() {
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
            static final int x = 0;
            static{
                staticT(x);
            }
            private static  int staticT(int x) {
                return 1 / x;
            }
        
            public static void main(String[] args) {
                new SomeClass();
            }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Caused by: java.lang.ArithmeticException: / by zero\n", null, null),
      Trinity.create("\tat SomeClass.staticT(SomeClass.java:7)\n", 7 + 7, 20),
      Trinity.create("\tat SomeClass.<clinit>(SomeClass.java:4)\n", 4 + 7, 9));
    checkFindMethodHandler(classText, traceAndPositions);
  }

  public void testSkipBridge() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class SomeClass {
            static class X {
                Object get() {
                    return null;
                }
            }
        
            static class Y extends X {
                String get() {
                    throw new IllegalArgumentException();
                }
            }
        
            public static void main(String[] args) {
                X x = new Y();
                x.get();
            }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.IllegalArgumentException\n", null, null),
      Trinity.create("\tat SomeClass$Y.get(SomeClass.java:11)\n", null, null),
      Trinity.create("\tat SomeClass$Y.get(SomeClass.java:9)\n", 9, 0),
      Trinity.create("\tat SomeClass.main(SomeClass.java:17)\n", null, null));
    checkFindMethodHandler(classText, traceAndPositions);
  }

  public void testSkipMultilineException() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class SomeClass {
            public static void main(String[] args) {
                String s = null;
        
                s
                        .
        
                        trim();
            }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot invoke \"String.trim()\" because \"s\" is null\n", null, null),
      Trinity.create("\tat SomeClass.main(SomeClass.java:9)\n", 9, 0));
    checkFindMethodHandler(classText, traceAndPositions);
  }

  private void checkFindMethodHandler(String classText, List<Trinity<String, Integer, Integer>> traceAndPositions) {
    PsiFile file = myFixture.configureByText("SomeClass.java", classText);
    Editor editor = myFixture.getEditor();
    assertEquals(classText, editor.getDocument().getText());
    ExceptionFilter filter = new ExceptionFilter(myFixture.getFile().getResolveScope());
    ExceptionLineRefiner previousRefiner = null;
    for (Trinity<String, Integer, Integer> line : traceAndPositions) {
      String stackLine = line.getFirst();
      filter.applyFilter(stackLine, stackLine.length());
      ExceptionLineRefiner refiner = filter.getLocationRefiner();
      ExceptionWorker.ParsedLine parsedLine = ExceptionWorker.parseExceptionLine(stackLine);
      if (parsedLine == null) {
        previousRefiner = refiner;
        continue;
      }
      Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
      int lineStart = document.getLineStartOffset(parsedLine.lineNumber - 1);
      int lineEnd = document.getLineEndOffset(parsedLine.lineNumber - 1);
      String className = parsedLine.classFqnRange.substring(stackLine);
      String methodName = parsedLine.methodNameRange.substring(stackLine);
      FindDivergedExceptionLineHandler
        handler = FindDivergedExceptionLineHandler.getFindMethodHandler(file, className, methodName, previousRefiner, lineStart, lineEnd, editor);
      previousRefiner = refiner;
      Integer row = line.getSecond();
      Integer column = line.getThird();
      if (column != null && row != null) {
        if (column == 0) {
          assertNull(handler);
          continue;
        }
        assertNotNull(handler);
        Collection<PsiElement> elements = handler.collector().get();
        if (elements.iterator().next() instanceof Navigatable navigatable) {
          navigatable.navigate(true);
          LogicalPosition actualPos = editor.getCaretModel().getLogicalPosition();
          assertEquals(new LogicalPosition(row - 1, column - 1), actualPos);
        }
        else {
          fail();
        }
      }
    }
  }
}
