// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

@SuppressWarnings("ALL")
public class LightOptimizeImportsTest extends LightCodeInsightFixtureTestCase {
  
  public void testSingleImportConflictingWith2Others() throws Exception {
    
    myFixture.addClass("package p; public class A1 {}");
    myFixture.addClass("package p; public class A2 {}");
    myFixture.addClass("package p; public class ArrayList {}");
    myFixture.addClass("package p1; public class ArrayList {}");
    
    myFixture.configureByText(StdFileTypes.JAVA, "\n" +
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
                                                 "}\n");
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 2;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    myFixture.checkResult("import p.*;\n" +
                          "import p1.ArrayList;\n" +
                          "\n" +
                          "import java.util.*;\n" +
                          "public class Optimize {\n" +
                          "    Class[] c = {\n" +
                          "            Collection.class,\n" +
                          "            List.class,\n" +
                          "            ArrayList.class,\n" +
                          "            A1.class,\n" +
                          "            A2.class\n" +
                          "    };\n" +
                          "}\n");
  }

  public void testStaticImportsOrder() throws Exception {
    
    myFixture.addClass("package p; public class C1 {" +
                       "    public static String Byte;\n" +
                       "    public static String Field2;" +
                       "}");
    myFixture.addClass("package p; public class C2 { " +
                       "    public static String Long;\n" +
                       "    public static String Field4;" +
                       "}");
    
    myFixture.configureByText(StdFileTypes.JAVA, "\n" +
                                                 "import static p.C1.*;\n" +
                                                 "import static p.C1.Byte;\n" +
                                                 "import static p.C2.Long;\n" +
                                                 "import static p.C2.*;\n" +
                                                 "\n" +
                                                 "public class Main {\n" +
                                                 "    public static void main(String[] args) {\n" +
                                                 "        System.out.println(Byte);\n" +
                                                 "        System.out.println(Field2);\n" +
                                                 "        System.out.println(Long);\n" +
                                                 "        System.out.println(Field4);\n" +
                                                 "    }\n" +
                                                 "}");
    
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile()));

    myFixture.checkResult("import static p.C1.Byte;\n" +
                          "import static p.C1.*;\n" +
                          "import static p.C2.Long;\n" +
                          "import static p.C2.*;\n" +
                          "\n" +
                          "public class Main {\n" +
                          "    public static void main(String[] args) {\n" +
                          "        System.out.println(Byte);\n" +
                          "        System.out.println(Field2);\n" +
                          "        System.out.println(Long);\n" +
                          "        System.out.println(Field4);\n" +
                          "    }\n" +
                          "}");
  }
}
