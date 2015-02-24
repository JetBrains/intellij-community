package com.intellij.codeInsight.unwrap;

public class UnwrapAnonymousTest extends UnwrapTestCase {
  public void testUnwrap() throws Exception {
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
  
  public void testUnwrapDeclaration() throws Exception {
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

  public void testUnwrapAssignment() throws Exception {
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

  public void testInsideMethodCall() throws Exception {
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

  public void testInsideAnotherAnonymous() throws Exception {
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

  public void testInsideAnotherAnonymousWithAssignment() throws Exception {
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

  public void testDeclarationWithMethodCall() throws Exception {
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

  public void testSeveralMethodCalls() throws Exception {
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

  public void testWhenCaretIsOnDeclaration() throws Exception {
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
  
  public void testEmptyClass() throws Exception {
    assertUnwrapped("{\n" +
                    "    Runnable r = new Run<caret>nable() {}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testDoNothingWithSeveralMethods() throws Exception {
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

  public void testReassignValue() throws Exception {
    assertUnwrapped("int i = new Comparable<String>() {\n" +
                    "            public int compareTo(String o) {\n" +
                    "                return <caret>0;\n" +
                    "            }\n" +
                    "        };\n",

                    "int i = 0;\n");
  }

  public void testReturnValue() throws Exception {
    assertUnwrapped("return new Comparable<Integer>() {\n" +
                    "    public int compareTo(Integer o) {\n" +
                    "        return <caret>0;\n" +
                    "    }\n" +
                    "};\n"
                    ,

                    "return 0;\n");
  }
}