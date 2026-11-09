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
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class InnerClassReferencedViaSubclassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new InnerClassReferencedViaSubclassInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "class MyClass {" +
      "    class InnerClass {" +
      "    }" +
      "    static class NestedClass {" +
      "        public static void m(){}" +
      "    }" +
      "    public static void method() {}" +
      "}",
      "class SubClass extends MyClass {" +
      "}"};
  }
  
  @Test
  public void testSimple1() {
    inspectionFixture.get().doTest("class X {" +
           "    void m() {" +
           "        SubClass./*Inner class 'InnerClass' declared in class 'MyClass' but referenced via subclass 'SubClass'*/InnerClass/**/ foo = new SubClass().new InnerClass();" +
           "    }" +
           "}");
  }
  
  @Test
  public void testSimple2() {
    inspectionFixture.get().doTest("class X {" +
           "    void m() {" +
           "        SubClass./*Inner class 'NestedClass' declared in class 'MyClass' but referenced via subclass 'SubClass'*/NestedClass/**/ bar1 = new SubClass./*Inner class 'NestedClass' declared in class 'MyClass' but referenced via subclass 'SubClass'*/NestedClass/**/();" +
           "    }" +
           "}");
  }
  
  @Test
  public void testNoWarn() {
    inspectionFixture.get().doTest("class X {" +
           "    void m() {" +
           "        MyClass.NestedClass bar2= null;" +
           "    }" +
           "}");
  }
  
  @Test
  public void testReferenceExpression() {
    inspectionFixture.get().doTest("class UnrelatedClass {" +
           "    void foo() {" +
           "        SubClass./*Inner class 'NestedClass' declared in class 'MyClass' but referenced via subclass 'SubClass'*/NestedClass/**/.m();" +
           "    }" +
           "}");
  }
}
