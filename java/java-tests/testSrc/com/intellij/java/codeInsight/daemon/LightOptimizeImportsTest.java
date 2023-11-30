// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

@SuppressWarnings("ALL")
public class LightOptimizeImportsTest extends LightJavaCodeInsightFixtureTestCase {
  
  public void testSingleImportConflictingWith2Others() throws Exception {
    
    myFixture.addClass("package p; public class A1 {}");
    myFixture.addClass("package p; public class A2 {}");
    myFixture.addClass("package p; public class ArrayList {}");
    myFixture.addClass("package p1; public class ArrayList {}");

    @Language("JAVA")
    String text = "\n" +
                  "import java.util.*;\n" +
                  "import p.*;\n" +
                  "import p1.ArrayList;\n" +
                  "import p1.ArrayList;\n" +
                  "public class Optimize {\n" +
                  "    Class[] c = {\n" +
                  "            Collection.class,\n" +
                  "            List.class,\n" +
                  "            ArrayList.class,\n" +
                  "            A1.class,\n" +
                  "            A2.class\n" +
                  "    };\n" +
                  "}\n";
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 2;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA")
    String result = "import p.*;\n" +
                    "import p1.ArrayList;\n" +
                    "\n" +
                    "import java.util.*;\n" +
                    "\n" +
                    "public class Optimize {\n" +
                    "    Class[] c = {\n" +
                    "            Collection.class,\n" +
                    "            List.class,\n" +
                    "            ArrayList.class,\n" +
                    "            A1.class,\n" +
                    "            A2.class\n" +
                    "    };\n" +
                    "}\n";
    myFixture.checkResult(result);
  }
  
  public void testDontExpandOnDemandStaticImports() throws Exception {
    myFixture.addClass("package p; public class A1 {" +
                       "  public static void f() {}" +
                       "}");
    myFixture.addClass("package p; public class A2 extends A1 {}");
    myFixture.addClass("package p; public class A3 extends A2 {}");
    myFixture.addClass("package p; public class A4 extends A3 {" +
                       "  public static void g() {}" +
                       "}");
    
    
    @Language("JAVA")
    String text = "\n" +
                  "import static p.A3.*;\n" +
                  "import static p.A4.*;\n" +
                  "public class Optimize {\n" +
                  "  {f();g();}" +
                  "}\n";
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 0;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    myFixture.checkResult(text);
  }

  public void testStaticImportsOrder() throws Exception {
    
    myFixture.addClass("package p; public class C1 {" +
                       "    public static class Byte {}\n" +
                       "    public static String Field2;" +
                       "}");
    myFixture.addClass("package p; public class C2 { " +
                       "    public static class Long {}\n" +
                       "    public static String Field4;" +
                       "}");

    @Language("JAVA")
    String text = "\n" +
                  "import static p.C1.*;\n" +
                  "import static p.C1.Byte;\n" +
                  "import static p.C2.Long;\n" +
                  "import static p.C2.*;\n" +
                  "\n" +
                  "public class Main {\n" +
                  "    public static void main(String[] args) {\n" +
                  "        System.out.println(Byte.class);\n" +
                  "        System.out.println(Field2);\n" +
                  "        System.out.println(Long.class);\n" +
                  "        System.out.println(Field4);\n" +
                  "    }\n" +
                  "}";
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA")
    String result = "import static p.C1.Byte;\n" +
                    "import static p.C1.*;\n" +
                    "import static p.C2.Long;\n" +
                    "import static p.C2.*;\n" +
                    "\n" +
                    "public class Main {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        System.out.println(Byte.class);\n" +
                    "        System.out.println(Field2);\n" +
                    "        System.out.println(Long.class);\n" +
                    "        System.out.println(Field4);\n" +
                    "    }\n" +
                    "}";
    myFixture.checkResult(result);
  }

  public void testStaticImportOnMethodFromSuperClass() {
    myFixture.addClass("package p; public class A {\n" +
                       "  public static void m1() {}\n" +
                       "  public static void m2() {}\n" +
                       "}");
    
    myFixture.addClass("package p; public class B extends A {\n" +
                       "  public static void m3() {}\n" +
                       "  public static void m4() {}\n" +
                       "}");

    @Language("JAVA")
    String text = "\n" +
                  "import static p.A.m1;\n" +
                  "import static p.A.m2;\n" +
                  "import static p.B.m3;\n" +
                  "import static p.B.m4;\n" +
                  "\n" +
                  "public class Main {\n" +
                  "    public static void main(String[] args) {\n" +
                  "      m1();\n" +
                  "      m2();\n" +
                  "      m3();\n" +
                  "      m4();\n" +
                  "    }\n" +
                  "}";
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    @Language("JAVA")
    String result = "import static p.A.*;\n" +
                    "import static p.B.*;\n" +
                    "\n" +
                    "public class Main {\n" +
                    "    public static void main(String[] args) {\n" +
                    "      m1();\n" +
                    "      m2();\n" +
                    "      m3();\n" +
                    "      m4();\n" +
                    "    }\n" +
                    "}";
    myFixture.checkResult(result);
  }

  public void testConflictingSingleImportUsedInReferenceQualifier() {
    myFixture.addClass("package p.p1; public class C {}");
    myFixture.addClass("package p.p1; public class D {}");
    myFixture.addClass("package p.p2;\n" +
                       "public class B{\n" +
                       "    public enum C { None, Some, All }\n" +
                       "    public static final int JUNK = 0;\n" +
                       "}");
    myFixture.configureByText(JavaFileType.INSTANCE, "package p.p2;\n" +
                                                     "import p.p1.*;\n" +
                                                     "import static p.p2.B.*;\n" +
                                                     "import static p.p2.B.C;\n" +
                                                     "public class A {\n" +
                                                     "    public static void main( String[] args )\n" +
                                                     "    {\n" +
                                                     "        new D();\n" +
                                                     "        System.out.println( C.None );\n" +
                                                     "        System.out.println( JUNK );\n" +
                                                     "    }\n" +
                                                     "}");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(false, "p", true));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    myFixture.checkResult("package p.p2;\n" +
                          "\n" +
                          "import p.p1.*;\n" +
                          "\n" +
                          "import static p.p2.B.C;\n" +
                          "import static p.p2.B.*;\n" +
                          "\n" +
                          "public class A {\n" +
                          "    public static void main( String[] args )\n" +
                          "    {\n" +
                          "        new D();\n" +
                          "        System.out.println( C.None );\n" +
                          "        System.out.println( JUNK );\n" +
                          "    }\n" +
                          "}");
  }

  public void testDontOptimizeIncompleteCode() {
    String fileText = "import java.util.ArrayList;\n" +
                  "public class A {\n" +
                  "    public static void main( String[] args)\n" +
                  "    {\n" +
                  "       ArrayList\n" +
                  "    }\n" +
                  "}";
    myFixture.configureByText(JavaFileType.INSTANCE, fileText);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));
    myFixture.checkResult(fileText);
  }
}
