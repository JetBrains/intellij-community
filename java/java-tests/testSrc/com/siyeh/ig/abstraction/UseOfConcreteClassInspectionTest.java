/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.abstraction;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UseOfConcreteClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UseOfConcreteClassInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[] {"class A {}"};
  }

  @Test
  public void testForeach() {
    inspectionFixture.get().doMemberTest("void m(java.util.List<A> l) {" +
                 "  for (/*Local variable 'a' of concrete class 'A'*/A/**/ a : l) {}" +
                 "}");
  }

  @Test
  public void testLocal() {
    inspectionFixture.get().doMemberTest("void m() {/*Local variable 'a' of concrete class 'A'*/A/**/ a;}");
  }

  @Test
  public void testParameter() {
    inspectionFixture.get().doMemberTest("void m(/*Parameter 'a' of concrete class 'A'*/A/**/ a) {}");
  }
  
  @Test
  public void testCatchParameter() {
    inspectionFixture.get().doMemberTest("void m() {try {toString();} catch (MyEx ex) {}} static class MyEx extends Error {}");
  }

  @Test
  public void testStaticField() {
    inspectionFixture.get().doMemberTest("static /*Static field 'myA' of concrete class 'A'*/A/**/ myA;");
  }

  @Test
  public void testInstanceField() {
    inspectionFixture.get().doMemberTest("/*Instance field 'myA' of concrete class 'A'*/A/**/ myA;");
  }

  @Test
  public void testMethodReturn() {
    inspectionFixture.get().doMemberTest("/*Method returns a concrete class 'A'*/A/**/ getA() {return null;}");
  }

  @Test
  public void testInstanceofInterfaces() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testCastToConcreteClass() {
    inspectionFixture.get().doTest();
  }
}
