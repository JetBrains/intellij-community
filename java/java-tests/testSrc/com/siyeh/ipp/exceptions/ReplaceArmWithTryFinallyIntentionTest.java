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
package com.siyeh.ipp.exceptions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see ReplaceArmWithTryFinallyIntention
 */
@TestApplication
public class ReplaceArmWithTryFinallyIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testSimple() {
    ipp.get().doTest(
      """
        import java.io.*;
        class C {
            void m() throws Exception {
                /*_Replace 'try-with-resources' with 'try finally'*/try (Reader r = new StringReader()) {
                    System.out.println(r);
                }
            }
        }""",

      """
        import java.io.*;
        class C {
            void m() throws Exception {
                Reader r = new StringReader();
                try {
                    System.out.println(r);
                } finally {
                    r.close();
                }
            }
        }""");
  }

  @Test
  public void testMixedResources() {
    ipp.get().doTest(
      """
        import java.io.*;
        class C {
            void m() throws Exception {
                Reader r1 = new StringReader();
                /*_Replace 'try-with-resources' with 'try finally'*/try (r1; Reader r2 = new StringReader()) {
                    System.out.println(r1 + ", " + r2);
                }
            }
        }""",

      """
        import java.io.*;
        class C {
            void m() throws Exception {
                Reader r1 = new StringReader();
                try {
                    Reader r2 = new StringReader();
                    try {
                        System.out.println(r1 + ", " + r2);
                    } finally {
                        r2.close();
                    }
                } finally {
                    r1.close();
                }
            }
        }""");
  }

  @Test
  public void testArmWithFinally() {
    ipp.get().doTest(
      """
        import java.io.*;
        class C {
            void m() throws Exception {
                /*_Replace 'try-with-resources' with 'try finally'*/try (Reader r = new StringReader()) {
                    System.out.println(r);
                } finally {
                  System.out.println();
                }
            }
        }""",

      """
        import java.io.*;
        class C {
            void m() throws Exception {
                try {
                    Reader r = new StringReader();
                    try {
                        System.out.println(r);
                    } finally {
                        r.close();
                    }
                } finally {
                  System.out.println();
                }
            }
        }"""
    );
  }

  @Test
  public void testMultipleResourcesWithCatch() {
    ipp.get().doTest(
      """
        import java.io.*;
        class C {
            void m(StringReader r1, StringReader r2) {
                try/*_Replace 'try-with-resources' with 'try finally'*/ (r1; r2) {
                    System.out.println(r1);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }
        """,

      """
        import java.io.*;
        class C {
            void m(StringReader r1, StringReader r2) {
                try {
                    try {
                        try {
                            System.out.println(r1);
                        } finally {
                            r2.close();
                        }
                    } finally {
                        r1.close();
                    }
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }
        """
    );
  }
}
