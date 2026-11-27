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

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

@TestApplication
public class JavaFormatterMultilineMethodCallParamsTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);


  @Test
  public void testChainedMethodInsideCall() {
    formatterFixture.get().doMethodTest(
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

  @Test
  public void testChainedMethodInsideCall_WithRParenOnNewLine() {
    formatterFixture.get().doMethodTest(
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
  
  @Test
  public void testLambdas() {
    formatterFixture.get().doTextTest(
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

  @Test
  public void testChainedMethodInsideCall_Shifted() {
    formatterFixture.get().doMethodTest(
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

  @Test
  public void testChainedMethodInsideCall_Shifted_WithRParentOnNewLine() {
    formatterFixture.get().doMethodTest(
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

  @Test
  public void testAnonClassAsParameter() {
    formatterFixture.get().doMethodTest(
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

  @Test
  public void testAnonClassWithRParent_OnNextLine() {
    formatterFixture.get().doMethodTest(
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
  
  @Test
  public void testMethodBracketsAlignment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = true;
    formatter.doTextTest(
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
