// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapAnonymousTest extends UnwrapTestCase {
  public void testUnwrap() {
    assertUnwrapped("{\n" +
                    "    new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            Sys<caret>tem.gc();\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }
  
  public void testUnwrapDeclaration() {
    assertUnwrapped("{\n" +
                    "    Runnable r = new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            Sys<caret>tem.gc();\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testUnwrapAssignment() {
    assertUnwrapped("{\n" +
                    "    Runnable r = null;\n" +
                    "    r = new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            Sys<caret>tem.gc();\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    Runnable r = null;\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testInsideMethodCall() {
    assertUnwrapped("{\n" +
                    "    foo(new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            Sys<caret>tem.gc();\n" +
                    "        }\n" +
                    "    });\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testInsideAnotherAnonymous() {
    assertUnwrapped("{\n" +
                    "    new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            int i = 0;\n" +
                    "            new Runnable() {\n" +
                    "                public void run() {\n" +
                    "                    Sys<caret>tem.gc();\n" +
                    "                }\n" +
                    "            };\n" +
                    "        }\n" +
                    "    };\n" +
                    "}\n",

                    "{\n" +
                    "    new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            int i = 0;\n" +
                    "            Sys<caret>tem.gc();\n" +
                    "        }\n" +
                    "    };\n" +
                    "}\n");
  }

  public void testInsideAnotherAnonymousWithAssignment() {
    assertUnwrapped("{\n" +
                    "    Runnable r = new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            int i = 0;\n" +
                    "            new Runnable() {\n" +
                    "                public void run() {\n" +
                    "                    Sys<caret>tem.gc();\n" +
                    "                }\n" +
                    "            };\n" +
                    "        }\n" +
                    "    };\n" +
                    "}\n",

                    "{\n" +
                    "    Runnable r = new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            int i = 0;\n" +
                    "            Sys<caret>tem.gc();\n" +
                    "        }\n" +
                    "    };\n" +
                    "}\n");
  }

  public void testDeclarationWithMethodCall() {
    assertUnwrapped("{\n" +
                    "    Object obj = foo(new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            Sys<caret>tem.gc();\n" +
                    "        }\n" +
                    "    });\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testSeveralMethodCalls() {
    assertUnwrapped("{\n" +
                    "    bar(foo(new Runnable() {\n" +
                    "        public void run() {\n" +
                    "            Sys<caret>tem.gc();\n" +
                    "        }\n" +
                    "    }));\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testWhenCaretIsOnDeclaration() {
    assertUnwrapped("{\n" +
                    "    Runnable r = new Run<caret>nable() {\n" +
                    "        public void run() {\n" +
                    "            System.gc();\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    System.gc();<caret>\n" +
                    "}\n");
  }
  
  public void testEmptyClass() {
    assertUnwrapped("{\n" +
                    "    Runnable r = new Run<caret>nable() {}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testDoNothingWithSeveralMethods() {
    assertUnwrapped("Runnable r = new Runnable() {\n" +
                    "    public void one() {\n" +
                    "        // method one\n" +
                    "        System.gc();\n" +
                    "    }\n" +
                    "    public void two() {\n" +
                    "        // method two\n" +
                    "        Sys<caret>tem.gc();\n" +
                    "    }\n" +
                    "}\n",

                    "Runnable r = new Runnable() {\n" +
                    "    public void one() {\n" +
                    "        // method one\n" +
                    "        System.gc();\n" +
                    "    }\n" +
                    "    public void two() {\n" +
                    "        // method two\n" +
                    "        Sys<caret>tem.gc();\n" +
                    "    }\n" +
                    "}\n");
  }

  public void testReassignValue() {
    assertUnwrapped("int i = new Comparable<String>() {\n" +
                    "            public int compareTo(String o) {\n" +
                    "                return <caret>0;\n" +
                    "            }\n" +
                    "        };\n",

                    "int i = 0;\n");
  }

  public void testReturnValue() {
    assertUnwrapped("return new Comparable<Integer>() {\n" +
                    "    public int compareTo(Integer o) {\n" +
                    "        return <caret>0;\n" +
                    "    }\n" +
                    "};\n"
                    ,

                    "return 0;\n");
  }
}