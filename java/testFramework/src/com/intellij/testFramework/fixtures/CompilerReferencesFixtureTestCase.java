// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.compiler.CompilerReferenceService;

public abstract class CompilerReferencesFixtureTestCase extends CompilerAwareFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    CompilerReferenceService.getInstance(getProject());
  }
}