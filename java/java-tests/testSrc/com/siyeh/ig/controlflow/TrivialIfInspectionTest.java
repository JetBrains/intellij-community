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
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class TrivialIfInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

  @Test
  public void testParenthesesReturn() {
    inspectionFixture.get().doMemberTest("boolean b(int[] array) {" +
                 "  /*'if' statement can be simplified*/if/**/ (array.length == 10) {" +
                 "    return (true);" +
                 "  } else{" +
                 "    return false;" +
                 "  }" +
                 "}");
  }

  @Test
  public void testParenthesesReturnNestedIf() {
    inspectionFixture.get().doMemberTest("""

                     boolean b(int[] array) {
                       if (array != null) {
                         int len = array.length;
                         /*'if' statement can be simplified*/if/**/(len == 10) return true;
                       }
                       return false;
                     }
                   """);
  }

  @Test
  public void testParenthesesAssignment() {
    inspectionFixture.get().doMemberTest("void b(int[] array) {" +
                 "  boolean result;" +
                 "  /*'if' statement can be simplified*/if/**/ (array.length == 10) {" +
                 "    result = (true);" +
                 "  } else{" +
                 "    result = (((false)));" +
                 "  }" +
                 "}");
  }

  @Test
  public void testReturn() {
    inspectionFixture.get().doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       /*'if' statement can be simplified*/if/**/ (x > 0) return true;
                       return false;
                   }
                   """);
  }

  @Test
  public void testReturnIgnoreChain() {
    inspectionFixture.get().doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       if (x > 0) return true;
                       return false;
                   }
                   """);
  }

  @Test
  public void testAssert() {
    inspectionFixture.get().doMemberTest("""

                     void test(int x) {
                       /*'if' statement can be simplified*/if/**/ (x > 20) assert false;
                   }
                   """);
  }

  @Test
  public void testIgnoreAssert() {
    inspectionFixture.get().doMemberTest("""

                     void test(int x) {
                       if (x > 20) assert false;
                   }
                   """);
  }

  @Test
  public void testReturnElseIf() {
    inspectionFixture.get().doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       else /*'if' statement can be simplified*/if/**/ (x > 0) return true;
                       else return false;
                   }
                   """);
  }

  @Test
  public void testReturnElseIfIgnoreChain() {
    inspectionFixture.get().doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       else if (x > 0) return true;
                       else return false;
                   }
                   """);
  }
  
  @Test
  public void testReturnEqualBranches() {
    // no warning: another inspection takes care about this
    inspectionFixture.get().doMemberTest("""

                     boolean b(int x) {
                       if (x > 20) return true;
                       else return true;
                   }
                   """);
  }

  @Test
  public void testMethodCall() {
    inspectionFixture.get().doMemberTest("""
                   void test(int x, Boolean foo) {
                     if (x == 0) System.out.println(foo);
                     else {
                       /*'if' statement can be simplified*/if/**/ (x > 0) test(0, true);
                       else test(0, false);
                     }
                   }""");
  }

  @Test
  public void testOverwrittenDeclaration() {
    inspectionFixture.get().doMemberTest("""
                   boolean test(int x) {
                     boolean result = false;
                     /*'if' statement can be simplified*/if/**/ (x == 0) result = true;
                     return result;
                   }""");
  }

  @Test
  public void testArrayWriteIncrement() {
    inspectionFixture.get().doMemberTest("""
                 void test(int[] arr, int idx, int i, int j) {
                   arr[idx++] = i;
                   if (i != j) {
                     arr[idx++] = j;
                   }
                 }""");
  }

  @Test
  public void testArrayWrite() {
    inspectionFixture.get().doMemberTest("""
                 void test(int[] arr, int idx, int i, int j) {
                   arr[idx] = i;
                   /*'if' statement can be simplified*/if/**/ (i != j) {
                     arr[idx] = j;
                   }
                 }""");
  }

  private static InspectionProfileEntry getInspection(String methodName) {
    TrivialIfInspection inspection = new TrivialIfInspection();
    String testName = methodName;
    if (testName.endsWith("IgnoreChain")) {
      inspection.ignoreChainedIf = true;
    }
    if (testName.endsWith("IgnoreAssert")) {
      inspection.ignoreAssertStatements = true;
    }
    return inspection;
  }
}
