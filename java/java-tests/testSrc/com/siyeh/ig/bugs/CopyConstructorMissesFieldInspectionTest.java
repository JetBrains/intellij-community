// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public class CopyConstructorMissesFieldInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new CopyConstructorMissesFieldInspection());

  @Test
  public void testCopyConstructorMissesField() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class Simple {" +
           "  private String name;" +
           "  public /*Copy constructor does not copy field 'name'*/Simple/**/(Simple simple) {}" +
           "}");
  }

  @Test
  public void testTwo() {
    inspectionFixture.get().doTest("class X {" +
           "  int one, two;" +
           "  /*Copy constructor does not copy fields 'one' and 'two'*/X/**/(X x) {}" +
           "}");
  }

  @Test
  public void testThree() {
    inspectionFixture.get().doTest("class X {" +
           "  int one, two, three;" +
           "  /*Copy constructor does not copy fields 'one', 'two' and 'three'*/X/**/(X x) {}" +
           "}");
  }

  @Test
  public void testFour() {
    inspectionFixture.get().doTest("class X {" +
           "  int one, two, three, four;" +
           "  /*Copy constructor does not copy 4 fields*/X/**/(X x) {}" +
           "}");
  }

  @Test
  public void testSimpleAssignment() {
    inspectionFixture.get().doTest("class X {" +
           "  int one, two;" +
           "  X(X x) {" +
           "    (this).one = x.one;" +
           "    two = x.two;" +
           "  }" +
           "}");
  }

  @Test
  public void testSimpleThisCall() {
    inspectionFixture.get().doTest("class X {" +
           "  int one, two;" +
           "  X(int one, int two) {" +
           "    this.one = one;" +
           "    this.two = two;" +
           "  }" +
           "  X(X x) {" +
           "    this(x.one, x.two);" +
           "  }" +
           "}");
  }

  @Test
  public void testGetterThisCall() {
    inspectionFixture.get().doTest("class X {" +
           "  int one, two;" +
           "  X(int one, int two) {" +
           "    this.one = one;" +
           "    this.two = two;" +
           "  }" +
           "  X(X x) {" +
           "    this(x.getOne(), x.getTwo());" +
           "  }" +
           "  int getOne() {" +
           "    return one;" +
           "  }" +
           "  int getTwo() {" +
           "    return two;" +
           "  }" +
           "}");
  }

  @Test
  public void testGettersAndSetters() {
    inspectionFixture.get().doTest("class X {" +
           "  int one, two;" +
           "  X(X x) {" +
           "    setOne(x.getOne());" +
           "    setTwo(x.getTwo());" +
           "  }" +
           "  int getOne() { return one; }" +
           "  void setOne(int one) { this.one = one; }" +
           "  int getTwo() { return two; }" +
           "  void setTwo(int two) { this.two = two; }" +
           "}");
  }

  @Test
  public void testTransient() {
    inspectionFixture.get().doTest("class X {" +
           "  transient int one;" +
           "  X(X x) {" +
           "    this();" +
           "  }" +
           "  X() {}" +
           "}");
  }

  @Test
  public void testMethodCalled() {
    inspectionFixture.get().doTest("import java.util.*;" +
           "class X {" +
           "  private List<String> list = new ArrayList<>();" +
           "  X(X x) {" +
           "    list.addAll(x.list);" +
           "  }" +
           "}");
  }

  @Test
  public void testContainsOwnCopy() {
    inspectionFixture.get().doTest("class X {" +
           "  int one, two;" +
           "  X copy;" +
           "  X(X x) {" +
           "    copy = x;" +
           "  }" +
           "}");
  }
}