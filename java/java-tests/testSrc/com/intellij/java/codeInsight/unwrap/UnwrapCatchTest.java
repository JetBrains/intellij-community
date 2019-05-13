// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

public class UnwrapCatchTest extends UnwrapTestCase {

  public void testTryWithResources() {
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
