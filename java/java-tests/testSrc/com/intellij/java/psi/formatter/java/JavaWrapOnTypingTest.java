// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class JavaWrapOnTypingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testWrapInsideTags() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
                                public class Hw {

                                    /**
                                     * @return index for the given relative path. Never {@code null}, but returned index may not exist (in which case,
                                     *
                                     * {@link Index#exists()} returns {@code false}). If the index do not exist, it may or may not be read-only (see <caret>)
                                     */
                                    public static void test() {
                                    }
                                }
                                """);

    myFixture.getEditor().getSettings().setWrapWhenTypingReachesRightMargin(true);
    
    myFixture.type('{');
    myFixture.type('@');
    
    myFixture.checkResult("""
                            public class Hw {

                                /**
                                 * @return index for the given relative path. Never {@code null}, but returned index may not exist (in which case,
                                 *
                                 * {@link Index#exists()} returns {@code false}). If the index do not exist, it may or may not be read-only (see
                                 * {@<caret>})
                                 */
                                public static void test() {
                                }
                            }
                            """);
  }

  @Test
  public void testWrapAtLineWithParameterHints() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE,
                              """
                                public class C {
                                    void m(int a, int b) {}
                                    void other() { m(1, 2<caret>); }
                                }""");
    myFixture.doHighlighting();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkResultWithInlays("""
                                        public class C {
                                            void m(int a, int b) {}
                                            void other() { m(<hint text="a:"/>1, <hint text="b:"/>2<caret>); }
                                        }""");
    });

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.getEditor().getSettings().setWrapWhenTypingReachesRightMargin(true);
      myFixture.getEditor().getSettings().setRightMargin(30);

      myFixture.type(" ");
      myFixture.checkResultWithInlays("""
                                        public class C {
                                            void m(int a, int b) {}
                                            void other() { m(<hint text="a:"/>1, <hint text="b:"/>2 <caret>); }
                                        }""");
      myFixture.type(" ");
      myFixture.doHighlighting();
      myFixture.checkResultWithInlays("""
                                        public class C {
                                            void m(int a, int b) {}
                                            void other() { m(<hint text="a:"/>1,
                                                    <hint text="b:"/>2  <caret>); }
                                        }""");
    });
  }
}
