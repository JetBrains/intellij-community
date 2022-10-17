// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structureView;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

public abstract class LightJavaStructureViewTestCaseBase extends LightJavaCodeInsightFixtureTestCase {
  @Language("JAVA")
  public static final String INTERFACE = """
    interface Interface {
     void g();
     void setI(int i);
     int getI();
    }""";
  @Language("JAVA")
  public static final String BASE = """
    public class Base {
     public Base() {}
     public void f() {}
     public String toString() { return null; }
     public void g() {}
     public void setX(int x) {}
     public int getX() {return 0;}
     protected int getZ() {return 0;}
     void setZ(int z) {}
    }""";
  @Language("JAVA")
  public static final String DERIVED = """
    public class Derived extends Base implements Interface {
     public class Inner {}
     public void f() {}
     public void g() {}
     public void setX(int x) {}
     public int getX() {return 0;}
     public void setY(int x) {}
     public int getY() {return 0;}
     public void setI(int i) {}
     public int getI() {return 0;}
     private int x;
     private int i;
    }""";

  public void init() {
    myFixture.addClass(INTERFACE);
    myFixture.addClass(BASE);
    myFixture.configureByText("Derived.java", DERIVED);
  }
}
