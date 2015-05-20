package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

public class UnwrapCatchTest extends UnwrapTestCase {

  public void testTryWithResources() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    assertUnwrapped("try (AutoCloseable r = null) {\n" +
                    "    System.out.println();\n" +
                    "} catch (ClassNotFoundException e) {\n" +
                    "    <caret>System.out.println();\n"+
                    "}",

                    "try (AutoCloseable r = null) {\n" +
                    "    System.out.println();\n" +
                    "}\n");
  }
}
