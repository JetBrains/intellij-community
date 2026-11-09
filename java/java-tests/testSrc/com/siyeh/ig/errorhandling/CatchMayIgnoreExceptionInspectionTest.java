package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class CatchMayIgnoreExceptionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses());

  private static LocalInspectionTool getInspection() {
    final CatchMayIgnoreExceptionInspection tool = new CatchMayIgnoreExceptionInspection();
    tool.m_ignoreCatchBlocksWithComments = true;
    tool.m_ignoreNonEmptyCatchBlock = false;
    return tool;
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package junit.framework; public abstract class TestCase {}"
    };
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doStatementTest("""
                          try {
                            throw new Exception();
                          } /*Empty 'catch' block*/catch/**/ (Exception e) {
                      }
                      """);
  }

  @Test
  public void testEmptyStatement() {
    inspectionFixture.get().doStatementTest(
      """
            try {
              throw new Exception();
            } /*Empty 'catch' block*/catch/**/ (Exception e) {
              ;
            }
        """);
  }

  @Test
  public void testComment() {
    inspectionFixture.get().doStatementTest("""
                          try {
                            throw new Exception();
                          } catch (Exception e) {
                            // comment
                          }
                      """);
  }

  @Test
  public void testIgnored() {
    inspectionFixture.get().doTest("""
             class C {
               void m() {
                 try {
                   throw new Exception();
                 } catch (Exception ignored) {
                 }
               }
             }
             """);
  }

  @Test
  public void testUnused() {
    inspectionFixture.get().doStatementTest("""
                          try {
                          }
                          catch (RuntimeException /*Unused 'catch' parameter 'e'*/e/**/) {
                            System.out.println("oops something wrong");
                          }
                        \
                      """);

  }

  @Test
  public void testUsedIgnored() {
    inspectionFixture.get().doStatementTest("""
                          try {
                          }
                          catch (RuntimeException /*'catch' parameter named 'ignore' is used*/ignore/**/) {
                            //comment
                            ignore.printStackTrace();
                          }
                        \
                      """);

  }

  @Test
  public void testUnusedComment() {
    inspectionFixture.get().doStatementTest("""
                          try {
                          }
                          catch (RuntimeException e) { \s
                            // we do not use exception object for a reason
                            System.out.println("something wrong");
                          }
                        \
                      """);
  }

  @Test
  public void testVMExceptionIgnored() {
    inspectionFixture.get().doStatementTest("""

                          try {
                            System.out.println("hello");
                          }
                          /*Unexpected VM exception like 'java.lang.NullPointerException' might be ignored in a 'catch' block*/catch/**/(Exception ex) {
                            if(ex instanceof ClassCastException) {
                              // report invalid cast
                              ex.printStackTrace();
                            }
                          }
                        \
                      """);
  }

  @Test
  public void testVMExceptionIgnored2() {
    inspectionFixture.get().doStatementTest("""

                          try {
                            System.out.println("hello");
                          }
                          /*Unexpected VM exception like 'java.lang.NullPointerException' might be ignored in a 'catch' block*/catch/**/(Exception ex) {
                            if(ex.getCause() instanceof ClassCastException) {
                              // report invalid cast
                              ex.printStackTrace();
                            }
                          }
                        \
                      """);
  }

  @Test
  public void testVMExceptionIgnored3() {
    inspectionFixture.get().doStatementTest("""

                          try {
                            System.out.println("hello");
                          }
                          /*Unexpected VM exception like 'java.lang.NullPointerException' might be ignored in a 'catch' block*/catch/**/(Exception ex) {
                            if("foo".equals(ex.getMessage())) {
                              // report some exception ignoring others
                              ex.printStackTrace();
                            }
                          }
                        \
                      """);
  }

  @Test
  public void testVMExceptionNotIgnored() {
    inspectionFixture.get().doStatementTest("""

                          try {
                            System.out.println("hello");
                          }
                          catch(Exception ex) {
                            if(ex instanceof ClassCastException) {
                              // report invalid cast
                              ex.printStackTrace();
                            }
                            throw ex;
                          }
                        \
                      """);
  }

  @Test
  public void testVMExceptionLeaked() {
    inspectionFixture.get().doTest("""
             class Exc {
               volatile Throwable exception;
               void test() {
                 //noinspection EmptyTryBlock
                 try {
                   //blah blah
                 }
                 catch (Throwable e) {
                   exception = e;
                 }
               }
             }""");
  }

  @Test
  public void testVMExceptionLeakedArray() {
    inspectionFixture.get().doTest("""
             class Exc {
               Throwable[] exception;
               void test() {
                 //noinspection EmptyTryBlock
                 try {
                   //blah blah
                 }
                 catch (Throwable e) {
                   exception[0] = e;
                 }
               }
             }""");
  }

  @Test
  public void testSneakyThrow() {
    inspectionFixture.get().doTest("""
             class Exc {
               Object apply() {
                 try {
                   return foo();
                 } catch (Throwable t) {
                   throw throwChecked(t);
                 }
               }
              \s
               native Object foo() throws Exception;
              \s
               @SuppressWarnings("unchecked")
               private static <T extends Throwable> RuntimeException throwChecked(Throwable t) throws T {
                 throw (T) t;
               }
             }
             """);
  }

}
