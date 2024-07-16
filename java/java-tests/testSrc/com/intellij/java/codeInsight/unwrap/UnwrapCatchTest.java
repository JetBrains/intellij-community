// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class UnwrapCatchTest extends UnwrapTestCase {

  public void testTryWithResources() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
    assertUnwrapped("""
                      try (AutoCloseable r = null) {
                          System.out.println();
                      } catch (ClassNotFoundException e) {
                          <caret>System.out.println();
                      }""",

                    """
                      try (AutoCloseable r = null) {
                          System.out.println();
                      }
                      """);
  }
}
