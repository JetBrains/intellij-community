// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.java.codeInsight.completion.AbstractCompilerAwareTest;

public abstract class CompilerReferencesTestBase extends AbstractCompilerAwareTest {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    CompilerReferenceService.getInstance(getProject());
  }
}
