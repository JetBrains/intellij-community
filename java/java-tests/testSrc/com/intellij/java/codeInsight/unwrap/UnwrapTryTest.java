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
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

public class UnwrapTryTest extends UnwrapTestCase {
  public void testTryEmpty() throws Exception {
    assertUnwrapped("{\n" +
                    "    try {\n" +
                    "        <caret>\n" +
                    "    } catch(Exception e) {}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testTryWithStatements() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "    int j;<caret>\n" +
                    "} catch(Exception e) {}\n",

                    "int i;\n" +
                    "int j;<caret>\n");
  }

  public void testTryWithCatches() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;<caret>\n" +
                    "} catch(RuntimeException e) {\n" +
                    "    int j;\n" +
                    "} catch(Exception e) {\n" +
                    "    int k;\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testTryWithFinally() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;<caret>\n" +
                    "} finally {\n" +
                    "    int j;\n" +
                    "}\n",

                    "int i;\n" +
                    "int j;<caret>\n");
  }

  public void testFinallyBlock() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "} finally {\n" +
                    "    int j;<caret>\n" +
                    "}\n",

                    "int i;\n" +
                    "int j;<caret>\n");
  }

  public void testFinallyBlockWithCatch() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "} catch(Exception e) {\n" +
                    "    int j;\n" +
                    "} finally {\n" +
                    "    int k;<caret>\n" +
                    "}\n",

                    "int i;\n" +
                    "int k;<caret>\n");
  }

  public void testCatchBlock() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "} catch(Exception e) {\n" +
                    "    int j;<caret>\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testManyCatchBlocks() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "} catch(RuntimeException e) {\n" +
                    "    int j;<caret>\n" +
                    "} catch(Exception e) {\n" +
                    "    int k;\n" +
                    "}\n",

                    "try {\n" +
                    "    int i;\n" +
                    "} <caret>catch(Exception e) {\n" +
                    "    int k;\n" +
                    "}\n");
  }

  public void testWatchBlockWithFinally() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "} catch(Exception e) {\n" +
                    "    int j;<caret>\n" +
                    "} finally {\n" +
                    "    int k;\n" +
                    "}\n",

                    "int i;\n" +
                    "int k;<caret>\n");
  }

  public void testTryFinally() throws Exception {
    assertOptions("try {\n" +
                  "} finally {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'try...'");
  }

  public void testTryWithOnlyOneCatch() throws Exception {
    assertOptions("try {\n" +
                  "} catch(Exception e) {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'try...'");
  }

  public void testTryWithSeveralCatches() throws Exception {
    assertOptions("try {\n" +
                  "} catch(Exception e) {\n" +
                  "} catch(Exception e) {\n" +
                  "    <caret>\n" +
                  "} catch(Exception e) {\n" +
                  "}\n",

                  "Remove 'catch...'",
                  "Unwrap 'try...'");
  }

  public void testTryWithResources() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_9);
    assertUnwrapped("AutoCloseable s = null;\n" +
                    "try (AutoCloseable r = null; s) {\n" +
                    "    <caret>System.out.println();\n" +
                    "}",

                    "AutoCloseable s = null;\n" +
                    "AutoCloseable r = null;\n" +
                    "System.out.println();\n");
  }
}
