// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.imports;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class UnusedImportGlobalInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/imports/globalInspection");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    boolean deleteUnusedModuleImports = javaSettings.isDeleteUnusedModuleImports();
    Disposer.register(myFixture.getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        javaSettings.setDeleteUnusedModuleImports(deleteUnusedModuleImports);
      }
    });
    javaSettings.setDeleteUnusedModuleImports(false);
  }

  @Test
  public void testInnerClassImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package pkg;
                         interface Action {
                             interface SuperInnerInterface {}
                         }""");
    myFixture.addClass("""
                         package pkg;
                         class ConceteAction implements Action {
                             interface SubInnerInterface {}
                         }""");
     doTest("""
              package pkg;
              /*Unused import 'import pkg.Action.SuperInnerInterface;'*/import pkg.Action.SuperInnerInterface;/**/
              import static pkg.ConceteAction.*;
              class Main {
                  SubInnerInterface innerClass;
                  void k()  {
                      new SuperInnerInterface() { };
                  }
              }""");
  }

  @Test
  public void testShadowedImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package java.util; public class HashTable {}");
    myFixture.addClass("package java.util; public class List {}");
    myFixture.addClass("package java.awt; public class List { public static final int ABORT = 1; }");
    myFixture.addClass("package java.awt; public class Component {}");
    doTest("""
             package test;
             import java.util.*;
             import java.awt.*;
             import java.awt.List;
             class Bar2 extends Component {
               void foo() {
                 new Hashtable();
                 System.out.println(List.ABORT);
               }
             }""");
  }

  @Test
  public void testStaticImport() {
    doTest("""
             /*Unused import 'import java.util.Map.*;'*/import java.util.Map.*;/**/
             import static java.util.Map.*;
             class X {
               void m(Entry e) {}
             }""");
  }

  @Test
  public void testStaticImport2() {
    doTest("""
             import static java.lang.Math.*;
             class X {
                 static {
                     System.out.println(""+PI);
                 }
             }""");
  }

  @Test
  public void testExactStaticImport() {
    doTest("""
             package a;
             import static java.lang.Math.abs;
             /*Unused import 'import static java.lang.Math.max;'*/import static java.lang.Math.max;/**/
             class Main {{
               abs(1);
             }}""");
  }

  @Test
  public void testUnresolvedReferencesInsideAmbiguousCallToImportedMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testNoHighlightingInInvalidCode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java",
                              """
                                import<EOLError descr="Identifier expected" textAttributesKey="ERRORS_ATTRIBUTES"></EOLError>
                                import java.util.*<error descr="';' expected" textAttributesKey="ERRORS_ATTRIBUTES"> </error>Math.<error descr="Cannot resolve symbol 'max'" textAttributesKey="WRONG_REFERENCES_ATTRIBUTES">max</error><error descr="Identifier expected" textAttributesKey="ERRORS_ATTRIBUTES">;</error>
                                class Main {}""");
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testStaticImportOnDemandConflict1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         public class Parent {
                           public static final int FOOBAR = 1;
                           public static class FooBar {}
                         }""");
    myFixture.addClass("""
                         package a;
                         public class FooBar {
                         }""");
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

  @Test
  public void testStaticImportOnDemandConflict2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         public class Parent {
                           public static final int FOOBAR = 1;
                           public static class FooBar {}
                         }""");
    myFixture.addClass("""
                         package a;
                         public class FooBar {
                         }""");
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

  @Test
  public void testInherited() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         class GrandParent {
                           public static final int FOOBAR = 1;
                         }""");
    myFixture.addClass("""
                         package a;
                         public class Parent extends GrandParent {
                         }""");
    doTest("""
             package b;
             import static a.Parent.*;
             class Main {
                 public static void main() {
                     int i = FOOBAR;
                 }
             }""");
  }

  @Test
  public void testNoWarning() {
    doTest("""
             import java.util.List;
             import java.util.ArrayList;
             import static java.lang.Integer.SIZE;
             class X {
                 private final List<Integer> list = new ArrayList<Integer>(SIZE);
                 public void add(int i) {
                     list.add(i);
                 }
             }""");
  }

  @Test
  public void testInnerClassAndMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package one; public class X { public class Inner {} public static void method() {}}");
    doTest("""
             package one;
             import static one.X.*
             import one.X.*;
              class Y {
                 void m() {
                     method();
                     Inner inner = new X().new Inner();
                 }
             }""");
  }

  @Test
  public void testRedundantImport() {
    doTest("""
             /*Unused import 'import java.util.ArrayList;'*/import java.util.ArrayList;/**/
             import java.util.ArrayList;
             class X {
                 void foo(ArrayList l) {}
             }""");
  }

  @Test
  public void testNoWarn() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package java.awt; public class List extends Component {}");
    myFixture.addClass("package java.awt; public class Component {}");
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

  @Test
  public void testOrderIsNotImportant() {
    doTest("""
             package a;
             import java.util.*;
             /*Unused import 'import java.util.List;'*/import java.util.List;/**/
             class X {{
               List list = new ArrayList();
             }}""");
  }

  @Test
  public void testConflictInSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class List {}");
    doTest("""
             package a;
             import java.util.List;
             import java.util.*;
             class X {{
               List list = new ArrayList();
             }}""");
  }

  @Test
  public void testUsedButUnresolved() {
    doTest("""
             package a;
             import java.util.List1;
             class X {{
               List1 list = null;
             }}""");
  }

  @Test
  public void testNoConflictInSamePackage() {
    doTest("""
             package a;
             /*Unused import 'import java.util.List;'*/import java.util.List;/**/
             import java.util.*;
             class X {{
               List list = new ArrayList();
             }}""");
  }

  @Test
  public void testModuleInfo() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class A {}");
    myFixture.addFileToProject("module-info.java", """
      import a.A;
    
      module my {
        uses A;
      }""");
    doTest();
  }

  @Test
  public void testSingleImportWithModuleImport() {
    doTest("""
      import module java.base;
      /*Unused import 'import java.util.List;'*/import java.util.List;/**/
      /*Unused import 'import java.util.ArrayList;'*/import java.util.ArrayList;/**/
    
      class Main {
          public static void main(String[] args) {
              List<String> a = new ArrayList<>();
          }
      }""");
  }

  @Test
  public void testImplicitLangImport() {
    doTest("""
      import java.util.List;
      import java.util.ArrayList;
      /*Unused import 'import java.lang.String;'*/import java.lang.String;/**/
    
      class Main {
          public static void main(String[] args) {
              List<String> a = new ArrayList<>();
          }
      }""");
  }

  @Test
  public void testRedundantImportWithImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(),
                           () -> {
                             doTest("""
                                      /*Unused import 'import java.util.List;'*/import java.util.List;/**/
                                    
                                      public static void main(String[] args) {
                                          List<String> a = new ArrayList<>();
                                      }
                                      """);
                           });
  }

  @Test
  public void testImportModuleWithRedundantPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(),
                           () -> {
                             doTest("""
                                      import module java.base;
                                      /*Unused import 'import java.util.*;'*/import java.util.*;/**/
                                    
                                      class Main {
                                          public static void main(String[] args) {
                                              List<String> a = new ArrayList<>();
                                          }
                                      }
                                      """);
                           });
  }

  @Test
  public void testImportImplicitModuleWithRedundantPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(),
                           () -> {
                             doTest("""
                                      /*Unused import 'import java.util.*;'*/import java.util.*;/**/
                                    
                                      public static void main(String[] args) {
                                          List<String> a = new ArrayList<>();
                                      }
                                      """);
                           });
  }

  @Test
  public void testRedundantImportModuleWithNotRedundantPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(),
                           () -> {
                             JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
                             myFixture.addClass("""
                                                  package a.b;
                                                
                                                  public final class List {
                                                  }
                                                  """);
                             doTest("""
                                      /*Unused import 'import module java.base;'*/import module java.base;/**/
                                      import a.b.*;
                                    
                                      class Main {
                                          public static void main(String[] args) {
                                              List a;
                                          }
                                      }
                                      """);
                           });
  }

  @Test
  public void testNotShowRedundantImportModuleWithNotRedundantPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(),
                           () -> {
                             JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
                             myFixture.addClass("""
                                                  package a.b;
                                                
                                                  public final class List {
                                                  }
                                                  """);
                             doTest("""
                                      import module java.base;
                                      import a.b.*;
                                    
                                      class Main {
                                          public static void main(String[] args) {
                                              List a;
                                          }
                                      }
                                      """);
                           });
  }

  @Test
  public void testDuplicatedRedundantImportModuleWithNotRedundantPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(),
                           () -> {
                             JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
                             myFixture.addClass("""
                                                  package a.b;
                                                
                                                  public final class List {
                                                  }
                                                  """);
                             doTest("""
                                      /*Unused import 'import module java.base;'*/ import module java.base;/**/
                                      import module java.base;
                                      import a.b.*;
                                    
                                      class Main {
                                          public static void main(String[] args) {
                                              List a;
                                          }
                                      }
                                      """);
                           });
  }

  @Test
  public void testStaticMethodInInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      package org.example;
    
      public interface Any {
          static void fromAny() {
          }
      }
      """);
    myFixture.addClass("""
      package org.example;
    
      public class Child implements Any {
          public static void fromChild(){}
      }""");
    myFixture.addFileToProject("Test.java", """
      package org.test;
    
      import static org.example.Any.*;
      import static org.example.Child.*;
    
      public class Test {
          public static void main(String[] args) {
              fromChild();
              fromAny();
          }
      }
      """);
    doTest();
  }

  @Test
  public void testDoubleStaticSameMethodInheritance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      package org.example;
    
      public class Child2 extends Child {
      }""");
    myFixture.addClass("""
      package org.example;
    
      public class Child extends Any {
          public static void test(){
              Any.test();
          }
      }""");
    myFixture.addClass("""
      package org.example;
    
      public class Any {
          public static void test() {
          }
      }""");
    myFixture.addFileToProject("Test.java", """
      package org.test;
    
      import static org.example.Child2.*;
    
      public class Test {
          public static void main(String[] args) {
              test();
          }
      }""");
    doTest();
  }

  private void doTest(String classText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(classText);

    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GlobalInspectionToolWrapper toolWrapper = new GlobalInspectionToolWrapper(new UnusedImportInspection());
    AnalysisScope scope = new AnalysisScope(myFixture.getProject());
    GlobalInspectionContextForTests globalContext =
      InspectionsKt.createGlobalContextForTool(scope, myFixture.getProject(), Collections.<InspectionToolWrapper<?, ?>>singletonList(toolWrapper));

    EdtTestUtil.runInEdtAndWait(() -> {
      InspectionTestUtil.runTool(toolWrapper, scope, globalContext);
      InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, new File(myFixture.getTestDataPath(), myFixture.getTestName(false)).getPath());
    });
  }

}