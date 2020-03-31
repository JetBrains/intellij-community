// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structureView;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

public abstract class LightJavaStructureViewTestCaseBase extends LightJavaCodeInsightFixtureTestCase {
  @Language("JAVA")
  public static final String INTERFACE = "interface Interface {" + "\n" +
                                         " void g();" + "\n" +
                                         " void setI(int i);" + "\n" +
                                         " int getI();" + "\n" +
                                         "}";
  @Language("JAVA")
  public static final String BASE = "public class Base {" + "\n" +
                                    " public Base() {}" + "\n" +
                                    " public void f() {}" + "\n" +
                                    " public String toString() { return null; }" + "\n" +
                                    " public void g() {}" + "\n" +
                                    " public void setX(int x) {}" + "\n" +
                                    " public int getX() {return 0;}" + "\n" +
                                    " protected int getZ() {return 0;}" + "\n" +
                                    " void setZ(int z) {}" + "\n" +
                                    "}";
  @Language("JAVA")
  public static final String DERIVED = "public class Derived extends Base implements Interface {" + "\n" +
                                       " public class Inner {}" + "\n" +
                                       " public void f() {}" + "\n" +
                                       " public void g() {}" + "\n" +
                                       " public void setX(int x) {}" + "\n" +
                                       " public int getX() {return 0;}" + "\n" +
                                       " public void setY(int x) {}" + "\n" +
                                       " public int getY() {return 0;}" + "\n" +
                                       " public void setI(int i) {}" + "\n" +
                                       " public int getI() {return 0;}" + "\n" +
                                       " private int x;" + "\n" +
                                       " private int i;" + "\n" +
                                       "}";

  public void init() {
    myFixture.addClass(INTERFACE);
    myFixture.addClass(BASE);
    myFixture.configureByText("Derived.java", DERIVED);
  }
}
