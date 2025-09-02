// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

@SuppressWarnings("ALL")
public class LightOptimizeImportsTest extends LightJavaCodeInsightFixtureTestCase {

  public void testLayoutOnDemandImportsFromTheSamePackageFirst() {
    myFixture.addClass("package a.a; public class A {}");
    myFixture.addClass("package a.a; public class C {}");
    myFixture.addClass("package a.b; public class A {}");
    myFixture.addClass("package a.b; public class B {}");
    myFixture.addClass("package a.b; public class Boolean {}");
    @Language("JAVA") String code = """
      package a;
      
      import a.a.A;
      import a.a.C;
      import a.b.B;
      import a.b.Boolean;
      
      class Main {
          A a;
          B b;
          C c;
          Boolean bool;
      }""";
    myFixture.configureByText(JavaFileType.INSTANCE, code);

    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.LAYOUT_ON_DEMAND_IMPORT_FROM_SAME_PACKAGE_FIRST = true;
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA") String result = """
      package a;
      
      import a.a.*;
      import a.a.A;
      import a.b.*;
      import a.b.Boolean;
      
      class Main {
          A a;
          B b;
          C c;
          Boolean bool;
      }""";
    myFixture.checkResult(result);
  }

  public void testImportLayoutStaticAndNonStaticImportsTogether() {
    myFixture.addClass("package aaa; public class AAA {}");
    myFixture.addClass("package aaa; public class BBB {" +
                       "  public static void x() {}" +
                       "}");
    myFixture.addClass("package aaa; public class CCC {}");

    @Language("JAVA") String text = """
      package main;
      
      import aaa.AAA;
      import aaa.CCC;
      import aaa.BBB;
      import java.util.ArrayList;
      import static aaa.BBB.x;
      
      public class Main {
          void usage() {
              new ArrayList<>();
              new AAA();
              new BBB();
              new CCC();
              x();
          }
      }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = false;
    javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_MODULE_IMPORTS);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "aaa", true));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA") String result = """
      package main;
      
      import java.util.ArrayList;
      
      import aaa.AAA;
      import aaa.BBB;
      import static aaa.BBB.x;
      import aaa.CCC;
            
      public class Main {
          void usage() {
              new ArrayList<>();
              new AAA();
              new BBB();
              new CCC();
              x();
          }
      }
      """;
    myFixture.checkResult(result);
  }
  
  public void testImportLayoutWithoutSubpackages() {
    myFixture.addClass("package aaa; public class AAA {}");
    myFixture.addClass("package bbb; public class BBB {}");
    myFixture.addClass("package ccc; public class CCC {}");

    @Language("JAVA") String text = """
      package main;
      
      import aaa.AAA;
      import bbb.BBB;
      import ccc.CCC;
      
      public class Main {
          void usage() {
              new AAA();
              new BBB();
              new CCC();
          }
      }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    //1 is module import
    javaSettings.IMPORT_LAYOUT_TABLE.insertEntryAt(new PackageEntry(false, "bbb", false), 1 + 1);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA") String result = """
      package main;
      
      import aaa.AAA;
      import ccc.CCC;
      import bbb.BBB;
      
      public class Main {
          void usage() {
              new AAA();
              new BBB();
              new CCC();
          }
      }
      """;
    myFixture.checkResult(result);
  }

  public void testSingleImportConflictingWith2Others() {
    myFixture.addClass("package p; public class A1 {}");
    myFixture.addClass("package p; public class A2 {}");
    myFixture.addClass("package p; public class ArrayList {}");
    myFixture.addClass("package p1; public class ArrayList {}");

    @Language("JAVA")
    String text = """
      import java.util.*;
      import p.*;
      import p1.ArrayList;
      import p1.ArrayList;
      public class Optimize {
          Class[] c = {
                  Collection.class,
                  List.class,
                  ArrayList.class,
                  A1.class,
                  A2.class
          };
      }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    // default import layout table is at com.intellij.psi.codeStyle.JavaCodeStyleSettings.initImportsByDefault()
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 2;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA")
    String result = """
      import p.*;
      import p1.ArrayList;
      
      import java.util.*;
      
      public class Optimize {
          Class[] c = {
                  Collection.class,
                  List.class,
                  ArrayList.class,
                  A1.class,
                  A2.class
          };
      }
      """;
    myFixture.checkResult(result);
  }
  
  public void testDontExpandOnDemandStaticImports() {
    myFixture.addClass("package p; public class A1 {" +
                       "  public static void f() {}" +
                       "}");
    myFixture.addClass("package p; public class A2 extends A1 {}");
    myFixture.addClass("package p; public class A3 extends A2 {}");
    myFixture.addClass("package p; public class A4 extends A3 {" +
                       "  public static void g() {}" +
                       "}");

    @Language("JAVA")
    String text = """
      import static p.A3.*;
      import static p.A4.*;
      public class Optimize {
        {f();g();}\
      }
      """;
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 0;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    myFixture.checkResult(text);
  }

  public void testStaticImportsOrder() {
    myFixture.addClass("""
                         package p; public class C1 {
                             public static class Byte {}
                             public static String Field2;
                         }""");
    myFixture.addClass("""
                         package p; public class C2 {
                             public static class Long {}
                             public static String Field4;
                         }""");

    @Language("JAVA")
    String text = """
      import static p.C1.*;
      import static p.C1.Byte;
      import static p.C2.Long;
      import static p.C2.*;
      
      public class Main {
          public static void main(String[] args) {
              System.out.println(Byte.class);
              System.out.println(Field2);
              System.out.println(Long.class);
              System.out.println(Field4);
          }
      }""";
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    javaSettings.LAYOUT_ON_DEMAND_IMPORT_FROM_SAME_PACKAGE_FIRST = false;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA")
    String result = """
      import static p.C1.Byte;
      import static p.C1.*;
      import static p.C2.Long;
      import static p.C2.*;
      
      public class Main {
          public static void main(String[] args) {
              System.out.println(Byte.class);
              System.out.println(Field2);
              System.out.println(Long.class);
              System.out.println(Field4);
          }
      }""";
    myFixture.checkResult(result);
  }

  public void testConflictInPackageWithImportInName() {
    myFixture.addClass("package a.importb; public class A {}");
    myFixture.addClass("package a.importb; public class B {}");
    myFixture.addClass("package a.importb; public class Boolean {}"); // conflict with java.lang.Boolean

    @Language("JAVA")
    String text = """
      package a;
      
      import a.importb.A;
      import a.importb.B;
      import a.importb.Boolean;
      
      public class Main {
          public static void main(String[] args) {
              A a;
              B b;
              Boolean boo;
          }
      }""";
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    javaSettings.LAYOUT_ON_DEMAND_IMPORT_FROM_SAME_PACKAGE_FIRST = false;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA")
    String result = """
      package a;
      
      import a.importb.Boolean;
      import a.importb.*;
      
      public class Main {
          public static void main(String[] args) {
              A a;
              B b;
              Boolean boo;
          }
      }""";
    myFixture.checkResult(result);
  }

  public void testStaticImportOnMethodFromSuperClass() {
    myFixture.addClass("""
                         package p; public class A {
                           public static void m1() {}
                           public static void m2() {}
                         }""");
    myFixture.addClass("""
                         package p; public class B extends A {
                           public static void m3() {}
                           public static void m4() {}
                         }""");

    @Language("JAVA")
    String text = """
      
      import static p.A.m1;
      import static p.A.m2;
      import static p.B.m3;
      import static p.B.m4;
      
      public class Main {
          public static void main(String[] args) {
            m1();
            m2();
            m3();
            m4();
          }
      }""";
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA")
    String result = """
      import static p.A.*;
      import static p.B.*;
      
      public class Main {
          public static void main(String[] args) {
            m1();
            m2();
            m3();
            m4();
          }
      }""";
    myFixture.checkResult(result);
  }

  public void testConflictingSingleImportUsedInReferenceQualifier() {
    myFixture.addClass("package p.p1; public class C {}");
    myFixture.addClass("package p.p1; public class D {}");
    myFixture.addClass("""
                         package p.p2;
                         public class B{
                             public enum C { None, Some, All }
                             public static final int JUNK = 0;
                         }""");
    @Language("JAVA") String text = """
      package p.p2;
      import p.p1.*;
      import static p.p2.B.*;
      import static p.p2.B.C;
      public class A {
          public static void main( String[] args )
          {
              new D();
              System.out.println( C.None );
              System.out.println( JUNK );
          }
      }""";
    myFixture.configureByText(JavaFileType.INSTANCE, text);

    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(false, "p", true));
    javaSettings.LAYOUT_ON_DEMAND_IMPORT_FROM_SAME_PACKAGE_FIRST = false;
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA") String result = """
      package p.p2;
      
      import p.p1.*;
      
      import static p.p2.B.C;
      import static p.p2.B.*;
      
      public class A {
          public static void main( String[] args )
          {
              new D();
              System.out.println( C.None );
              System.out.println( JUNK );
          }
      }""";
    myFixture.checkResult(result);
  }

  public void testDontOptimizeIncompleteCode() {
    @Language("JAVA") String fileText = """
      import java.util.ArrayList;
      public class A {
          public static void main( String[] args)
          {
             ArrayList
          }
      }""";
    myFixture.configureByText(JavaFileType.INSTANCE, fileText);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));
    myFixture.checkResult(fileText);
  }
}