// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

public class UnwrapTryTest extends UnwrapTestCase {
  public void testTryEmpty() {
    assertUnwrapped("{\n" +
                    "    try {\n" +
                    "        <caret>\n" +
                    "    } catch(Exception e) {}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testTryWithStatements() {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "    int j;<caret>\n" +
                    "} catch(Exception e) {}\n",

                    "int i;\n" +
                    "int j;<caret>\n");
  }

  public void testTryWithCatches() {
    assertUnwrapped("try {\n" +
                    "    int i;<caret>\n" +
                    "} catch(RuntimeException e) {\n" +
                    "    int j;\n" +
                    "} catch(Exception e) {\n" +
                    "    int k;\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testTryWithFinally() {
    assertUnwrapped("try {\n" +
                    "    int i;<caret>\n" +
                    "} finally {\n" +
                    "    int j;\n" +
                    "}\n",

                    "int i;\n" +
                    "int j;<caret>\n");
  }

  public void testFinallyBlock() {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "} finally {\n" +
                    "    int j;<caret>\n" +
                    "}\n",

                    "int i;\n" +
                    "int j;<caret>\n");
  }

  public void testFinallyBlockWithCatch() {
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

  public void testCatchBlock() {
    assertUnwrapped("try {\n" +
                    "    int i;\n" +
                    "} catch(Exception e) {\n" +
                    "    int j;<caret>\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testManyCatchBlocks() {
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

  public void testWatchBlockWithFinally() {
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

  public void testTryFinally() {
    assertOptions("try {\n" +
                  "} finally {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'try...'");
  }

  public void testTryWithOnlyOneCatch() {
    assertOptions("try {\n" +
                  "} catch(Exception e) {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'try...'");
  }

  public void testTryWithSeveralCatches() {
    assertOptions("try {\n" +
                  "} catch(Exception e) {\n" +
                  "} catch(Exception e) {\n" +
                  "    <caret>\n" +
                  "} catch(Exception e) {\n" +
                  "}\n",

                  "Remove 'catch...'",
                  "Unwrap 'try...'");
  }

  public void testTryWithResources() {
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
