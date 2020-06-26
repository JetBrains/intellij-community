// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration.createTest;

import com.intellij.lang.LanguageExtension;

/**
 * @author Max Medvedev
 */
public final class TestGenerators extends LanguageExtension<TestGenerator> {
  public static final TestGenerators INSTANCE = new TestGenerators();

  private TestGenerators() {
    super("com.intellij.testGenerator", new JavaTestGenerator());
  }
}
