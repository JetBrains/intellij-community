/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.formatter.java;

public class JavaFormatterMultilineMethodCallParamsTest extends AbstractJavaFormatterTest {


  public void testChainedMethodInsideCall() {
    doMethodTest(
      """
        call(new StringBuilder()
        .append("aaa")
        .append("bbbb"));""",
      """
        call(new StringBuilder()
                .append("aaa")
                .append("bbbb"));"""
    );
  }

  public void testChainedMethodInsideCall_WithRParenOnNewLine() {
    doMethodTest(
      """
        call(new StringBuilder()
        .append("aaa")
        .append("bbbb")
        );""",
      """
        call(new StringBuilder()
                .append("aaa")
                .append("bbbb")
        );"""
    );
  }
  
  public void testLambdas() {
    doTextTest(
      """
        public class Main {
            public static void main(String... args) throws Exception {
                RatpackServer.start(server -> server
                                .handlers(chain -> chain
                                                .get(ctx -> ctx.render("Hello World!"))
                                                .get(":name", ctx -> ctx.render("Hello " + ctx.getPathTokens().get("name") + "!"))
                                )
                );
            }
        }""",
      """
        public class Main {
            public static void main(String... args) throws Exception {
                RatpackServer.start(server -> server
                        .handlers(chain -> chain
                                .get(ctx -> ctx.render("Hello World!"))
                                .get(":name", ctx -> ctx.render("Hello " + ctx.getPathTokens().get("name") + "!"))
                        )
                );
            }
        }"""
    );
  }

  public void testChainedMethodInsideCall_Shifted() {
    doMethodTest(
      """
        call(new StringBuilder()
        .append("aaa")
        .append("bbbb"),
        "aaaa");""",
      """
        call(new StringBuilder()
                        .append("aaa")
                        .append("bbbb"),
                "aaaa");"""
    );
  }

  public void testChainedMethodInsideCall_Shifted_WithRParentOnNewLine() {
    doMethodTest(
      """
        call(new StringBuilder()
        .append("aaa")
        .append("bbbb"),
        "aaaa"
        );""",
      """
        call(new StringBuilder()
                        .append("aaa")
                        .append("bbbb"),
                "aaaa"
        );"""
    );
  }

  public void testAnonClassAsParameter() {
    doMethodTest(
      """
        call(new Runnable() {
        @Override
        public void run() {
        }
        });""",
      """
        call(new Runnable() {
            @Override
            public void run() {
            }
        });"""
    );
  }

  public void testAnonClassWithRParent_OnNextLine() {
    doMethodTest(
      """
        foo(new Runnable() {
            @Override
            public void run() {
            }
        }
        );
        """,
      """
        foo(new Runnable() {
                @Override
                public void run() {
                }
            }
        );
        """
    );
  }
  
  public void testMethodBracketsAlignment() {
    getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = true;
    doTextTest(
      """
        public class Foo {
          private IntPredicate example1() {
            return Boo.combine(Boo.p1(),
                               Boo.p2());
          }
         \s
          private boolean example2() {
            return IntStream.range(0, 4).allMatch(Boo.combine(Boo.p1(),
                                                              Boo.p2()));
          }
        }""",
      """
        public class Foo {
            private IntPredicate example1() {
                return Boo.combine(Boo.p1(),
                        Boo.p2());
            }

            private boolean example2() {
                return IntStream.range(0, 4).allMatch(Boo.combine(Boo.p1(),
                        Boo.p2()));
            }
        }"""
    );
  }
  


}
