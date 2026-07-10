// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

//JEP 532 for java 27 is the same as JEP 520 for java 26 (without any changes)
public class LightPrimitivePatternsHighlightingWithTightenedDominance27Test extends LightPrimitivePatternsHighlightingWithTightenedDominanceTest {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_27;
  }
}