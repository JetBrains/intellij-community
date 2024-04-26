/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.resources;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 * @noinspection resource
 */
public class AutoCloseableResourceInspectionTest extends LightJavaInspectionTestCase {


  public void testCorrectClose() {
    // No highlighting because str was closed and we have ignoreResourcesWithClose option set
    //noinspection EmptyTryBlock
    doTest("import java.io.*;" +
           "class X {" +
           "    public static void m() throws IOException {" +
           "        FileInputStream str;" +
           "        str = new FileInputStream(\"bar\");" +
           "        try {" +
           "        } finally {" +
           "            str.close();" +
           "        }" +
           "    }" +
           "}");
  }

  public void testEscape() {
    doTest("import java.io.*;" +
           "class X {" +
           "  static void m() throws IOException {" +
           "    n(new FileInputStream(\"file.name\"));" +
           "  }" +
           "  static void n(Closeable c) {" +
           "    System.out.println(c);" +
           "  }" +
           "}");
  }

  public void testEscape2() {
    final AutoCloseableResourceInspection inspection = new AutoCloseableResourceInspection();
    inspection.anyMethodMayClose = false;
    myFixture.enableInspections(inspection);
    doTest("import java.io.*;" +
           "class X {" +
           "  static void m() throws IOException {" +
           "    n(new /*'FileInputStream' used without 'try'-with-resources statement*/FileInputStream/**/(\"file.name\"));" +
           "  }" +
           "  static void n(Closeable c) {" +
           "    System.out.println(c);" +
           "  }" +
           "}");
  }

  public void testEscape3() {
    doTest("import java.io.*;" +
           "class X {" +
           "  static void m() throws IOException {" +
           "    System.out.println(new FileInputStream(\"file.name\"));" +
           "  }" +
           "}");
  }

  public void testARM() {
    mockSql();
    doTest("""
             import java.sql.*;
             class X {
               static void m(Driver driver) throws SQLException {
                 try (Connection connection = driver.connect("jdbc", null);
                   PreparedStatement statement = connection.prepareStatement("SELECT *");
                   ResultSet resultSet = statement.executeQuery()) {
                   while (resultSet.next()) { resultSet.getMetaData(); }
                 } catch(Exception e) {}
               }
             }""");
  }

  private void mockSql() {
    addEnvironmentClass("package java.sql;\n" +
                        "public interface Driver { Connection connect(String s, Object o) throws SQLException;}");
    addEnvironmentClass("package java.sql;\n" +
                        "public interface Connection extends AutoCloseable { PreparedStatement prepareStatement(String s);}");
    addEnvironmentClass("package java.sql;\n" +
                        "public interface PreparedStatement extends AutoCloseable { ResultSet executeQuery();}");
    addEnvironmentClass("package java.sql;\n" +
                        "public class SQLException extends Exception {}");
    addEnvironmentClass("""
                          package java.sql;
                          public interface ResultSet extends AutoCloseable {
                            boolean next();
                            void getMetaData();
                          }""");
  }

  public void testSimple() {
    mockSql();
    doTest("import java.sql.*;" +
           "class X {" +
           "  static void m(Driver driver) throws SQLException {" +
           "    driver./*'Connection' used without 'try'-with-resources statement*/connect/**/(\"jdbc\", null);" +
           "  }" +
           "}");
  }

  public void testSystemOut() {
    doTest("class X {" +
           "  static void m(String s) {" +
           "    System.out.printf(\"asdf %s\", s);" +
           "    System.err.format(\"asdf %s\", s);" +
           "  }" +
           "}");
  }

  public void testMethodReference() {
    doTest("import java.util.*;" +
           "class X {" +
           "  void m(List<String> list) {" +
           "    final Z<String, Y> f = /*'Y' used without 'try'-with-resources statement*/Y::new/**/;" +
           "  }" +
           "  class Y implements java.io.Closeable {" +
           "    Y(String s) {}" +
           "    @Override public void close() throws java.io.IOException {}" +
           "  }" +
           "  interface Z<T, R> {\n" +
           "    R apply(T t);" +
           "  }" +
           "}");
  }

  public void testFormatter() {
    doTest("import java.util.*;" +
           "class TryWithResourcesFalsePositiveForFormatterFormat {" +
           "    public static void useFormatter( Formatter output ) {" +
           "        output.format( \"Hello, world!%n\" );" +
           "    }" +
           "}");
  }

  public void testWriterAppend() {
    doTest("import java.io.*;" +
           "class A {" +
           "    private static void write(Writer writer) throws IOException {" +
           "        writer.append(\"command\");" +
           "    }" +
           "}");
  }

  public void testScanner() {
    doTest("import java.util.Scanner;" +
           "class A {" +
           "    static void a() throws java.io.IOException {" +
           "        try (Scanner scanner = new Scanner(\"\").useDelimiter(\"\\\\A\")) {" +
           "            String sconf = scanner.next();" +
           "            System.out.println(sconf);" +
           "        }" +
           "    }" +
           "}");
  }

  public void testTernary() {
    //noinspection EmptyTryBlock
    doTest("""
             import java.io.*;

             class X {
               private static void example(int a) throws IOException {
                 try (FileOutputStream byteArrayOutputStream = a > 0 ? new FileOutputStream("/etc/passwd") : new
                   FileOutputStream("/etc/shadow")) {
                 }
               }
             }""");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  public void testFilesMethod() {
    addEnvironmentClass("""
                          package java.nio.file;
                          import java.util.stream.Stream;
                          public final class Files {
                            public static Stream<String> lines(Path path) {return Stream.empty();}
                          }""");
    doTest("""
             import java.io.*;
             import java.nio.file.Files;
             import java.util.stream.Stream;
             class X {
               private static void example(int a) throws IOException {
                 Stream<String> s = Files.<warning descr="'Stream<String>' used without 'try'-with-resources statement">lines</warning>(null);
               }
             }""");
  }

  public void testClosedResource() {
    doTest("""
             import java.io.*;

             class X {
               private static void example(int a) throws IOException {
                 new FileOutputStream("").close();
               }
             }""");
  }

  public void testContractParameter() {
    doTest("""
             import java.io.*;

             class X {
               private static void example(Object o) throws IOException {
                 InputStream is = o.getClass().<warning descr="'InputStream' used without 'try'-with-resources statement">getResourceAsStream</warning>("");
                     InputStream is2 = test(is);
               }

               static <T> T test(T t) {
                 return t;
               }
             }""");
  }

  public void testContractThis() {
    doTest("""
             import java.io.*;

             class X implements Closeable {
               @Override
               public void close() throws IOException {}

               private static void example(Object o) throws IOException {
                     new <warning descr="'X' used without 'try'-with-resources statement">X</warning>().doX();
               }

               private X doX() {
                 return this;
               }
             }""");
  }

  public void testCallThenClose() {
    // No highlighting because str was closed and we have ignoreResourcesWithClose option set
    doTest("""
             import java.io.*;

             class X implements AutoCloseable {
               @Override
               public void close() {}
               static native X makeX();
             }
             class Other {
               private static void example() {
                 final X x = X.makeX();
                 x.close();
               }
             }""");
  }


  public void testLambdaReturnsResource() {
    doTest("""
             import java.io.*;

             class X implements AutoCloseable {
               @Override
               public void close() {}

               private static void example() {
                 consume(() -> new X());
               }
              \s
               interface Consumer { X use();}
               private static native X getX();
               private static native void consume(Consumer x);
             }""");
  }

  public void testLambdaNotReturnsResource() {
    doTest("""
             import java.io.*;

             class X implements AutoCloseable {
               @Override
               public void close() {}

               private static void example() {
                 consume(() -> new <warning descr="'X' used without 'try'-with-resources statement">X</warning>());
               }
              \s
               interface Runnable { void run();}
               private static native X getX();
               private static native void consume(Runnable x);
             }""");
  }


  public void testResourcePassedToConstructorOfResource() {
    doTest("""
             import java.io.*;

             class X implements AutoCloseable {
               X(X other) {}
               X() {}
               @Override public void close() {}
               private static void example(X other) {
                 new X(other);
               }
             }""");
  }

  public void testCreatedResourcePassedToConstructor() {
    doTest("""
             import java.io.*;

             class X implements AutoCloseable {
               X(X other) {}
               X() {}
               @Override public void close() {}
               private static void example(X other) {
                 new X(new X());
               }
             }""");
  }

  public void testCreatedResourcePassedToConstructorAsVar() {
    doTest("""
             import java.io.*;

             class X implements AutoCloseable {
               X(X other) {}
               X() {}
               @Override public void close() {}
               private static void example(X other) {
                 X resource = new X();
                 new X(resource);
               }
             }""");
  }

  public void testResourceAssigned() {
    doTest(
      """
        class X implements AutoCloseable {
          @Override public void close() {}
          private static X example(boolean cond, X other) {
            X x;
            if (cond) {
              x = new X();
            } else {
              x = other;
            }
            return x;
          }
        }""");
  }

  public void testResourceObjectMethodReturnsAnotherResource() {
    // Expect error in case when it happens inside the resource itself
    doTest(
      """
        class X implements AutoCloseable {
          @Override public void close() {}
          private static void example() {
            <warning descr="'X' used without 'try'-with-resources statement">createPossiblyDependantResource</warning>();
          }
          private static X createPossiblyDependantResource() { return null; }
        }""");
  }

  public void testResourceEscapesToConstructor() {
    doTest(
      """
        class X implements AutoCloseable {
          @Override public void close() {}
          private static void example() {
            X x = createX();
            if (x != null) {
              new EscapeTo(10, x);
            }
          }
          native static X createX();
        }
        class EscapeTo {
          X x;
          EscapeTo(int y, X x) {this.x = x;}
          native void doStuff();
        }""");
  }

  public void testIgnoredStreamPassedAsArgumentToNonIgnored() {
    doTest(
      """
        import java.io.ByteArrayOutputStream;
        import java.io.IOException;
        import java.io.ObjectOutputStream;

        class Test{
          void test() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ObjectOutputStream oos;
            oos = new ObjectOutputStream(outputStream);
          }
        }""");
  }

  public void testConstructorClosesResource() {
    doTest(
      """
        import java.io.ByteArrayOutputStream;
        import java.io.IOException;
        import java.io.ObjectOutputStream;

        class Test{
          Test(AutoCloseable ac){}
          void test(){
            AC ac = new AC();
            new Test(ac);
          }
        }
        class AC implements AutoCloseable {
          @Override public void close(){}
        }""");
  }

  public void testResourceClosed() {
    doTest(
      """
        class Test{
          void test(){
            AC ac = ACHolder.makeAc();
            useAc(new ACHolder(ac));
          }
          void useAc(ACHolder holder) {}
        }
        class ACHolder {
          private final AC ac;
          public static AC makeAc() { return null;}
          ACHolder(AC ac) {this.ac = ac;}
        }
        class AC implements AutoCloseable {
          @Override public void close(){}
        }""");
  }

  public void testGetMethodNotConsideredAsResource() {
    doTest(
      """
        class Test{
          void test(){
            getAc();
          }
          AC getAc() {return null;}
        }
        class AC implements AutoCloseable {
          @Override public void close(){}
        }""");
  }

  public void testBuilderMustNotBeTriggered() {
    doTest(
      """
        class Test{
          void test(){
            AC ac = makeAc();
            ac.use();
            ac.close();
          }
          AC makeAc() {return null;}
        }
        class AC implements AutoCloseable {
          AC use() {return this; }
          @Override public void close(){}
        }""");
  }

  public void testFieldInitializationAsEscape() {
    doTest(
      """
        class AC implements AutoCloseable {
          AC ac = new AC();
          @Override public void close(){}
        }""");
  }

  public void testTryAsReference() {
    doTest(
      """
        class AC implements AutoCloseable {
          void test(boolean condition) {
            AC ac = condition ? new AC() : null;
            if (ac == null) return;
            try (AC ac1 = ac) {
              ac1.test(false);
            }
          }
          @Override public void close(){}
        }""");
  }
  public void testReferenceReousrce() {
    doTest(
      """
        import java.io.BufferedReader;
        import java.io.IOException;

        class Scratch {

          public static void main(String[] args) {
            BufferedReader bufferedReader = new BufferedReader(null);
            try (bufferedReader) {
              System.out.println();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }

        }""");
  }

  public void testClose() {
    doTest(
      """
        class AC implements AutoCloseable {
          void test() {
            final AC ac = new AC();
            try {
              work();
            } finally {
              ac.close();
            }
          }
          native void work();
          @Override public void close(){}
        }""");
  }

  public void test232779() {
    doTest(
      """
        class AutoCloseableSample {
          public OAuth2AccessToken authenticateOAuth(String code) {
            OAuth20Service service = <warning descr="'OAuth20Service' used without 'try'-with-resources statement">makeOAuth2Service</warning>();
            try {
              return service.getAccessToken(code);
            }
            catch (RuntimeException e) {
              return null;
            }
          }

          native OAuth20Service makeOAuth2Service();

          private static class OAuth2AccessToken {}

          private static class OAuth20Service implements AutoCloseable {
            native public OAuth2AccessToken getAccessToken(String code);
            @Override public void close() {}
          }
        }""");
  }

  public void testOnlyLastBuilderCallInConsidered() {
    doTest(
      """
        class AutoCloseableSample {
          void test() {
            final AC res = new AC().withX(10).withX(12);
            res.close();
          }
        }
        class AC implements AutoCloseable {
          native AC withX(int x);
          @Override public void close(){}
        }""");
  }

  public void testEscapeLoop1() {
    doTest(
      """
        import java.io.FileOutputStream;
        import java.io.IOException;
        import java.io.OutputStream;
        import java.util.List;
        
        class AutoCloseableSample {
          private void processFiles(List<String> files) throws IOException {
            OutputStream stream = null;
            try {
              for (String file : files) {
                stream = new <warning descr="'FileOutputStream' used without 'try'-with-resources statement">FileOutputStream</warning>(file);
              }
            } finally {
              stream.close();
            }
          }
        }
        """);
  }

  public void testEscapeLoop2() {
    doTest(
      """
        import java.io.FileOutputStream;
        import java.io.IOException;
        import java.io.OutputStream;
        import java.util.List;
        
        class AutoCloseableSample {
          private static void processFiles(List<String> files) throws IOException {
            OutputStream stream = null;
            try {
              for (String file : files) {
                stream = new FileOutputStream(file);
                stream.close();
              }
            } finally {
              stream.close();
            }
          }
        }
        """);
  }

  public void testIgnoreFix() {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    try {
      myFixture.configureByText("X.java", """
        import java.io.InputStream;
                
        class X {
          native InputStream produce();
          
          void test() {
            InputStream is = <caret>produce();
            int i = is.read();
          }
        }
        """);
      IntentionAction intention = myFixture.findSingleIntention("Ignore 'AutoCloseable' returned by this method");
      myFixture.checkIntentionPreviewHtml(intention, "Add method <code>X.produce()</code> to the list of ignored methods");
      myFixture.launchAction(intention);
      var inspection = (AutoCloseableResourceInspection)InspectionProfileManager.getInstance(getProject()).getCurrentProfile()
        .getUnwrappedTool("AutoCloseableResource", getFile());
      assertContainsElements(inspection.myMethodMatcher.getClassNames(), "X");
      assertContainsElements(inspection.myMethodMatcher.getMethodNamePatterns(), "produce");
    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
    }
  }

  @Override
  protected LocalInspectionTool getInspection() {
    AutoCloseableResourceInspection inspection = new AutoCloseableResourceInspection();
    inspection.ignoreConstructorMethodReferences = false;
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public final class Formatter implements java.io.Closeable {" +
      "    public Formatter format(String format, Object ... args) {" +
      "      return this;" +
      "    }" +
      "}",
      "package java.util;" +
      "public final class Scanner implements java.io.Closeable {" +
      "    public Scanner(String source) {}" +
      "    public Scanner useDelimiter(String pattern) {" +
      "         return this;" +
      "    }" +
      "    public String next() {" +
      "        return this;" +
      "    }" +
      "}"
    };
  }
}
