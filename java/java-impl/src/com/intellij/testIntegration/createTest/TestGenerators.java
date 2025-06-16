// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration.createTest;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public final class TestGenerators extends LanguageExtension<TestGenerator> {
  public static final TestGenerators INSTANCE = new TestGenerators();

  private TestGenerators() {
    super("com.intellij.testGenerator", new JavaTestGenerator());
  }

  public static @NotNull Collection<TestGenerator> allForLanguageWithDefault(@NotNull Language language) {
    Set<TestGenerator> generators = new LinkedHashSet<>(INSTANCE.allForLanguage(language));
    generators.add(INSTANCE.getDefaultImplementation());
    return generators;
  }
}
