package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class CatchMayIgnoreExceptionInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected LocalInspectionTool getInspection() {
    final CatchMayIgnoreExceptionInspection tool = new CatchMayIgnoreExceptionInspection();
    tool.m_ignoreCatchBlocksWithComments = true;
    tool.m_ignoreNonEmptyCatchBlock = false;
    return tool;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package junit.framework; public abstract class TestCase {}"
    };
  }

  public void testSimple() {
    doStatementTest("""
                          try {
                            throw new Exception();
                          } /*Empty 'catch' block*/catch/**/ (Exception e) {
                      }
                      """);
  }

  public void testEmptyStatement() {
    doStatementTest(
      """
            try {
              throw new Exception();
            } /*Empty 'catch' block*/catch/**/ (Exception e) {
              ;
            }
        """);
  }

  public void testComment() {
    doStatementTest("""
                          try {
                            throw new Exception();
                          } catch (Exception e) {
                            // comment
                          }
                      """);
  }

  public void testIgnored() {
    doTest("""
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

  public void testUnused() {
    doStatementTest("""
                          try {
                          }
                          catch (RuntimeException /*Unused 'catch' parameter 'e'*/e/**/) {
                            System.out.println("oops something wrong");
                          }
                        \
                      """);

  }

  public void testUsedIgnored() {
    doStatementTest("""
                          try {
                          }
                          catch (RuntimeException /*'catch' parameter named 'ignore' is used*/ignore/**/) {
                            //comment
                            ignore.printStackTrace();
                          }
                        \
                      """);

  }

  public void testUnusedComment() {
    doStatementTest("""
                          try {
                          }
                          catch (RuntimeException e) { \s
                            // we do not use exception object for a reason
                            System.out.println("something wrong");
                          }
                        \
                      """);
  }

  public void testVMExceptionIgnored() {
    doStatementTest("""

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

  public void testVMExceptionIgnored2() {
    doStatementTest("""

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

  public void testVMExceptionIgnored3() {
    doStatementTest("""

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

  public void testVMExceptionNotIgnored() {
    doStatementTest("""

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

  public void testVMExceptionLeaked() {
    doTest("""
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

  public void testVMExceptionLeakedArray() {
    doTest("""
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

  public void testSneakyThrow() {
    doTest("""
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
