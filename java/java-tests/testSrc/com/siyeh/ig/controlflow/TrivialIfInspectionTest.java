/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class TrivialIfInspectionTest extends LightJavaInspectionTestCase {

  public void testParenthesesReturn() {
    doMemberTest("boolean b(int[] array) {" +
                 "  /*'if' statement can be simplified*/if/**/ (array.length == 10) {" +
                 "    return (true);" +
                 "  } else{" +
                 "    return false;" +
                 "  }" +
                 "}");
  }

  public void testParenthesesReturnNestedIf() {
    doMemberTest("""

                     boolean b(int[] array) {
                       if (array != null) {
                         int len = array.length;
                         /*'if' statement can be simplified*/if/**/(len == 10) return true;
                       }
                       return false;
                     }
                   """);
  }

  public void testParenthesesAssignment() {
    doMemberTest("void b(int[] array) {" +
                 "  boolean result;" +
                 "  /*'if' statement can be simplified*/if/**/ (array.length == 10) {" +
                 "    result = (true);" +
                 "  } else{" +
                 "    result = (((false)));" +
                 "  }" +
                 "}");
  }

  public void testReturn() {
    doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       /*'if' statement can be simplified*/if/**/ (x > 0) return true;
                       return false;
                   }
                   """);
  }

  public void testReturnIgnoreChain() {
    doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       if (x > 0) return true;
                       return false;
                   }
                   """);
  }

  public void testAssert() {
    doMemberTest("""

                     void test(int x) {
                       /*'if' statement can be simplified*/if/**/ (x > 20) assert false;
                   }
                   """);
  }

  public void testIgnoreAssert() {
    doMemberTest("""

                     void test(int x) {
                       if (x > 20) assert false;
                   }
                   """);
  }

  public void testReturnElseIf() {
    doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       else /*'if' statement can be simplified*/if/**/ (x > 0) return true;
                       else return false;
                   }
                   """);
  }

  public void testReturnElseIfIgnoreChain() {
    doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       else if (x > 0) return true;
                       else return false;
                   }
                   """);
  }
  
  public void testReturnEqualBranches() {
    // no warning: another inspection takes care about this
    doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       else return true;
                   }
                   """);
  }

  public void testMethodCall() {
    doMemberTest("""
                   void test(int x, Boolean foo) {
                     if (x == 0) System.out.println(foo);
                     else {
                       /*'if' statement can be simplified*/if/**/ (x > 0) test(0, true);
                       else test(0, false);
                     }
                   }""");
  }

  public void testOverwrittenDeclaration() {
    doMemberTest("""
                   boolean test(int x) {
                     boolean result = false;
                     /*'if' statement can be simplified*/if/**/ (x == 0) result = true;
                     return result;
                   }""");
  }

  public void testArrayWriteIncrement() {
    doMemberTest("""
                 void test(int[] arr, int idx, int i, int j) {
                   arr[idx++] = i;
                   if (i != j) {
                     arr[idx++] = j;
                   }
                 }""");
  }

  public void testArrayWrite() {
    doMemberTest("""
                 void test(int[] arr, int idx, int i, int j) {
                   arr[idx] = i;
                   /*'if' statement can be simplified*/if/**/ (i != j) {
                     arr[idx] = j;
                   }
                 }""");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    TrivialIfInspection inspection = new TrivialIfInspection();
    String testName = getTestName(false);
    if (testName.endsWith("IgnoreChain")) {
      inspection.ignoreChainedIf = true;
    }
    if (testName.endsWith("IgnoreAssert")) {
      inspection.ignoreAssertStatements = true;
    }
    return inspection;
  }
}
