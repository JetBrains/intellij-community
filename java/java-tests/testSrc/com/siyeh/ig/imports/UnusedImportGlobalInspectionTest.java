// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.imports;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;

import java.io.File;
import java.util.Collections;

public class UnusedImportGlobalInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/InspectionGadgets/test/com/siyeh/igtest/imports/globalInspection";
  }

  public void testInnerClassImport() {
    myFixture.addClass("package pkg;" +
                       "interface Action {" +
                       "    interface SuperInnerInterface {}" +
                       "}");
    myFixture.addClass("package pkg;" +
                       "class ConceteAction implements Action {" +
                       "    interface SubInnerInterface {}" +
                       "}");
     doTest("package pkg;" +
           "/*Unused import 'import pkg.Action.SuperInnerInterface;'*/import pkg.Action.SuperInnerInterface;/**/" +
           "import static pkg.ConceteAction.*;" +
           "class Main {" +
           "    SubInnerInterface innerClass;" +
           "    void k()  {" +
           "        new SuperInnerInterface() { };" +
           "    }" +
           "}");
  }

  public void testShadowedImports() {
    myFixture.addClass("package java.util; public class HashTable {}");
    myFixture.addClass("package java.util; public class List {}");
    myFixture.addClass("package java.awt; public class List { public static final int ABORT = 1; }");
    myFixture.addClass("package java.awt; public class Component {}");
    doTest("package test;" +
           "import java.util.*;" +
           "import java.awt.*;" +
           "import java.awt.List;" +
           "class Bar2 extends Component {" +
           "  void foo() {" +
           "    new Hashtable();" +
           "    System.out.println(List.ABORT);" +
           "  }" +
           "}");
  }

  public void testStaticImport() {
    doTest("/*Unused import 'import java.util.Map.*;'*/import java.util.Map.*;/**/" +
           "import static java.util.Map.*;" +
           "class X { " +
           "  void m(Entry e) {}" +
           "}");
  }

  public void testStaticImport2() {
    doTest("import static java.lang.Math.*;" +
           "class X {" +
           "    static {" +
           "        System.out.println(\"\"+PI);" +
           "    }" +
           "}");
  }

  public void testExactStaticImport() {
    doTest("""
             package a;
             import static java.lang.Math.abs;
             /*Unused import 'import static java.lang.Math.max;'*/import static java.lang.Math.max;/**/
             class Main {{
               abs(1);
             }}""");
  }

  
  public void testUnresolvedReferencesInsideAmbiguousCallToImportedMethod() {
    myFixture.addClass("""
                         package a; public class A {
                          public static void foo(Object o) {}
                          public static void foo(String s) {}
                         }""");
    doTest("""
             import static a.A.foo;
             class Test {
                  {
                       foo(<error descr="Cannot resolve method 'unresolvedMethodCall()'">unresolvedMethodCall</error>());
                  }
             }""");
  }

  
  public void testNoHighlightingInInvalidCode() {
    myFixture.configureByText("a.java",
                              """
                                import<EOLError></EOLError>
                                import java.util.*<error> </error><error>Math.max;</error>
                                class Main {}""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testStaticImportOnDemandConflict1() {
    myFixture.addClass("package a;" +
                       "public class Parent {" +
                       "  public static final int FOOBAR = 1;" +
                       "  public static class FooBar {}" +
                       "}");
    myFixture.addClass("package a;" +
                       "public class FooBar {" +
                       "}");
    doTest("""
             package b;
             import a.*;
             import a.FooBar;
             import static a.Parent.*;
             class Main {
                 public static void main() {
                     Parent parent = new Parent();
                     int i = FOOBAR;
                     FooBar foobar = new FooBar();
                 }
             }""");
  }

  public void testStaticImportOnDemandConflict2() {
    myFixture.addClass("package a;" +
                       "public class Parent {" +
                       "  public static final int FOOBAR = 1;" +
                       "  public static class FooBar {}" +
                       "}");
    myFixture.addClass("package a;" +
                       "public class FooBar {" +
                       "}");
    doTest("""
             package b;
             import a.*;
             import static a.Parent.*;
             import static a.Parent.FooBar;
             class Main {
                 public static void main() {
                     Parent parent = new Parent();
                     int i = FOOBAR;
                     FooBar foobar = new FooBar();
                 }
             }""");
  }

  public void testInherited() {
    myFixture.addClass("package a;" +
                       "class GrandParent {" +
                       "  public static final int FOOBAR = 1;" +
                       "}");
    myFixture.addClass("package a;" +
                       "public class Parent extends GrandParent {" +
                       "}");
    doTest("""
             package b;
             import static a.Parent.*;
             class Main {
                 public static void main() {
                     int i = FOOBAR;
                 }
             }""");
  }

  public void testNoWarning() {
    doTest("import java.util.List;" +
           "import java.util.ArrayList;" +
           "import static java.lang.Integer.SIZE;" +
           "class X {" +
           "    private final List<Integer> list = new ArrayList<Integer>(SIZE);" +
           "    public void add(int i) {" +
           "        list.add(i);" +
           "    }" +
           "}");
  }

  public void testInnerClassAndMethod() {
    myFixture.addClass("package one; public class X { public class Inner {} public static void method() {}}");
    doTest("package one;" +
           "import static one.X.*; " +
           "import one.X.*;" +
           " class Y { " +
           "    void m() {" +
           "        method(); " +
           "        Inner inner = new X().new Inner();" +
           "    }" +
           "}");
  }

  public void testRedundantImport() {
    doTest("/*Unused import 'import java.util.ArrayList;'*/import java.util.ArrayList;/**/" +
           "import java.util.ArrayList;" +
           "class X { " +
           "    void foo(ArrayList l) {}" +
           "}");
  }

  public void testNoWarn() {
    myFixture.addClass("package java.awt; public class List extends Component {}");
    doTest("""
             import javax.swing.*;
             import java.awt.*;
             import java.util.*;
             import java.util.List;

             class ImportTest extends Component {

               Collection<String> c;
               List<Integer> l;
               JComponent jc;
             }""");
  }

  public void testOrderIsNotImportant() {
    doTest("package a;" +
           "import java.util.*;" +
           "/*Unused import 'import java.util.List;'*/import java.util.List;/**/" +
           "class X {{" +
           "  List list = new ArrayList();" +
           "}}");

  }

  public void testConflictInSamePackage() {
    myFixture.addClass("package a; public class List {}");
    doTest("package a;" +
           "import java.util.List;" +
           "import java.util.*;" +
           "class X {{" +
           "  List list = new ArrayList();" +
           "}}");
  }

  public void testUsedButUnresolved() {
    doTest("package a;" +
           "import java.util.List1;" +
           "class X {{" +
           "  List1 list = null;" +
           "}}");
  }

  public void testNoConflictInSamePackage() {
    doTest("package a;" +
           "/*Unused import 'import java.util.List;'*/import java.util.List;/**/" +
           "import java.util.*;" +
           "class X {{" +
           "  List list = new ArrayList();" +
           "}}");
  }

  public void testModuleInfo() {
    myFixture.addClass("package a; public class A {}");
    myFixture.addFileToProject("module-info.java", "import a.A;\n" +
                                                   "module my {" +
                                                   "  uses A;" +
                                                   "}");
    doTest();
  }

  private void doTest(String classText) {
    myFixture.addClass(classText);

    doTest();
  }

  private void doTest() {
    GlobalInspectionToolWrapper toolWrapper = new GlobalInspectionToolWrapper(new UnusedImportInspection());
    AnalysisScope scope = new AnalysisScope(myFixture.getProject());
    GlobalInspectionContextForTests globalContext =
      InspectionsKt.createGlobalContextForTool(scope, getProject(), Collections.<InspectionToolWrapper<?, ?>>singletonList(toolWrapper));

    InspectionTestUtil.runTool(toolWrapper, scope, globalContext);
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, new File(getTestDataPath(), getTestName(false)).getPath());
  }

}