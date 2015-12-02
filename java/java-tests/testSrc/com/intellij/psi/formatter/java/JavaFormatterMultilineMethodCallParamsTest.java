/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.formatter.java;

public class JavaFormatterMultilineMethodCallParamsTest extends AbstractJavaFormatterTest {


  public void testChainedMethodInsideCall() {
    doMethodTest(
      "call(new StringBuilder()\n" +
      ".append(\"aaa\")\n" +
      ".append(\"bbbb\"));",
      "call(new StringBuilder()\n" +
      "        .append(\"aaa\")\n" +
      "        .append(\"bbbb\"));"
    );
  }

  public void testChainedMethodInsideCall_WithRParenOnNewLine() {
    doMethodTest(
      "call(new StringBuilder()\n" +
      ".append(\"aaa\")\n" +
      ".append(\"bbbb\")\n" +
      ");",
      "call(new StringBuilder()\n" +
      "        .append(\"aaa\")\n" +
      "        .append(\"bbbb\")\n" +
      ");"
    );
  }
  
  public void testLambdas() {
    doTextTest(
      "public class Main {\n" +
      "    public static void main(String... args) throws Exception {\n" +
      "        RatpackServer.start(server -> server\n" +
      "                        .handlers(chain -> chain\n" +
      "                                        .get(ctx -> ctx.render(\"Hello World!\"))\n" +
      "                                        .get(\":name\", ctx -> ctx.render(\"Hello \" + ctx.getPathTokens().get(\"name\") + \"!\"))\n" +
      "                        )\n" +
      "        );\n" +
      "    }\n" +
      "}",
      "public class Main {\n" +
      "    public static void main(String... args) throws Exception {\n" +
      "        RatpackServer.start(server -> server\n" +
      "                .handlers(chain -> chain\n" +
      "                        .get(ctx -> ctx.render(\"Hello World!\"))\n" +
      "                        .get(\":name\", ctx -> ctx.render(\"Hello \" + ctx.getPathTokens().get(\"name\") + \"!\"))\n" +
      "                )\n" +
      "        );\n" +
      "    }\n" +
      "}"
    );
  }

  public void testChainedMethodInsideCall_Shifted() {
    doMethodTest(
      "call(new StringBuilder()\n" +
      ".append(\"aaa\")\n" +
      ".append(\"bbbb\"),\n" +
      "\"aaaa\");",
      "call(new StringBuilder()\n" +
      "                .append(\"aaa\")\n" +
      "                .append(\"bbbb\"),\n" +
      "        \"aaaa\");"
    );
  }

  public void testChainedMethodInsideCall_Shifted_WithRParentOnNewLine() {
    doMethodTest(
      "call(new StringBuilder()\n" +
      ".append(\"aaa\")\n" +
      ".append(\"bbbb\"),\n" +
      "\"aaaa\"\n" +
      ");",
      "call(new StringBuilder()\n" +
      "                .append(\"aaa\")\n" +
      "                .append(\"bbbb\"),\n" +
      "        \"aaaa\"\n" +
      ");"
    );
  }

  public void testAnonClassAsParameter() {
    doMethodTest(
      "call(new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}\n" +
      "});",
      "call(new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "    }\n" +
      "});"
    );
  }

  public void testAnonClassWithRParent_OnNextLine() {
    doMethodTest(
      "foo(new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "    }\n" +
      "}\n" +
      ");\n",
      "foo(new Runnable() {\n" +
      "        @Override\n" +
      "        public void run() {\n" +
      "        }\n" +
      "    }\n" +
      ");\n"
    );
  }
  
  public void testMethodBracketsAlignment() {
    getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = true;
    doTextTest(
      "public class Foo {\n"                                                +
      "  private IntPredicate example1() {\n"                               +
      "    return Boo.combine(Boo.p1(),\n"                                  +
      "                       Boo.p2());\n"                                 +
      "  }\n"                                                               +
      "  \n"                                                                +
      "  private boolean example2() {\n"                                    +
      "    return IntStream.range(0, 4).allMatch(Boo.combine(Boo.p1(),\n"   +
      "                                                      Boo.p2()));\n" +
      "  }\n"                                                               +
      "}", 
      "public class Foo {\n"                                                  +
      "    private IntPredicate example1() {\n"                               +
      "        return Boo.combine(Boo.p1(),\n"                                +
      "                Boo.p2());\n"                                          +
      "    }\n"                                                               +
      "\n"                                                                  +
      "    private boolean example2() {\n"                                    +
      "        return IntStream.range(0, 4).allMatch(Boo.combine(Boo.p1(),\n" +
      "                Boo.p2()));\n" +
      "    }\n"                                                               +
      "}"
    );
  }
  


}
