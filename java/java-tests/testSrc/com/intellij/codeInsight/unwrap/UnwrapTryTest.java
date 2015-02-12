package com.intellij.codeInsight.unwrap;

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
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    assertUnwrapped("try (AutoCloseable r = null) {\n" +
                    "    <caret>System.out.println();\n" +
                    "}",

                    "System.out.println();\n");
  }
}
