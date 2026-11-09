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
package com.siyeh.ig.visibility;

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
public class MethodOverridesPrivateMethodInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new MethodOverridesInaccessibleMethodOfSuperInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package test;" +
      "public class Super implements java.io.Serializable {" +
      "private void readObject(java.io.ObjectInputStream s){}" +
      "private void writeObject(java.io.ObjectOutputStream s){}" +
      "private void other() {}" +
      "}",
      "package test; " +
      "public class Parent<T> {" +
       "void foo(){}" +
       "void bar(T t){}" +
      "}"
    };
  }

  @Test
  public void testSerializationMethod() {
    inspectionFixture.get().doTest("import test.Super;" +
           "class A extends Super {" +
           "  private void readObject(java.io.ObjectInputStream s) {}" +
           "}");
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("import test.Super;" +
           "class B extends Super {" +
           "  private void /*Method 'other()' overrides a 'private' method of a superclass*/other/**/() {}" +
           "}");
  }
  
  @Test
  public void testPackageLocal() {
    inspectionFixture.get().doTest("import test.Parent;" +
           "class Child extends Parent<String> {" +
           "  void /*Method 'foo()' overrides a package-private method of a superclass located in another package*/foo/**/() {}" +
           "}");
  }

  @Test
  public void testGenericSuperClass() {
    inspectionFixture.get().doTest("import test.Parent;" +
           "class Child extends Parent<String> {" +
           "  void /*Method 'bar()' overrides a package-private method of a superclass located in another package*/bar/**/(String s) {}" +
           "}");
  }
}
