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

import com.siyeh.ipp.IPPTestCase;

/**
 * @see MergeNestedTryStatementsIntention
 * @author Bas Leijdekkers
 */
public class MergeNestedTryStatementsIntentionTest extends IPPTestCase {
  public void testSimple() {
    doTest(
      """
        import java.io.*;
        class C {
            void foo(File file1, File file2) throws IOException {
                /*_Merge nested 'try' statements*/try (FileInputStream in = new FileInputStream(file1)) {
                    try (FileOutputStream out = new FileOutputStream(file2)) {
                        System.out.println(in + ", " + out);
                    }
                }
            }
        }""",

      """
        import java.io.*;
        class C {
            void foo(File file1, File file2) throws IOException {
                try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {
                    System.out.println(in + ", " + out);
                }
            }
        }""");
  }

  public void testWithoutAndWithResources() {
    doTest(
      """
        import java.io.*;
        class C {
            void foo(File file) {
                /*_Merge nested 'try' statements*/try {
                    try (InputStreamReader r = new InputStreamReader(new FileInputStream(file), "utf-8")) {
                      System.out.println(r);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }""",

      """
        import java.io.*;
        class C {
            void foo(File file) {
                try (InputStreamReader r = new InputStreamReader(new FileInputStream(file), "utf-8")) {
                    System.out.println(r);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }""");
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed", "TryWithIdenticalCatches"})
  public void testOldStyle() {
    doTest(
      """
        import java.io.*;
        class C {
            void foo(File file1) {
                /*_Merge nested 'try' statements*/try {
                    try {
                        FileInputStream in = new FileInputStream(file1);
                    } catch (FileNotFoundException e) {
                        // log
                    }
                } catch (Exception e) {
                    // log
                }
            }
        }""",

      """
        import java.io.*;
        class C {
            void foo(File file1) {
                try {
                    FileInputStream in = new FileInputStream(file1);
                } catch (FileNotFoundException e) {
                    // log
                } catch (Exception e) {
                    // log
                }
            }
        }""");
  }

  public void testMixedResources() {
    doTest(
      """
        import java.io.*;
        class C {
            void m() throws Exception {
                Reader r1 = new StringReader();
                /*_Merge nested 'try' statements*/try (r1) {
                    try (Reader r2 = new StringReader()) {
                        System.out.println(r1 + ", " + r2);
                    }
                }
            }
        }""",

      """
        import java.io.*;
        class C {
            void m() throws Exception {
                Reader r1 = new StringReader();
                try (r1; Reader r2 = new StringReader()) {
                    System.out.println(r1 + ", " + r2);
                }
            }
        }""");
  }
}
