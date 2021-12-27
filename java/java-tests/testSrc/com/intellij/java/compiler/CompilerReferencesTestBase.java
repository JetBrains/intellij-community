// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.java.codeInsight.completion.AbstractCompilerAwareTest;

public abstract class CompilerReferencesTestBase extends AbstractCompilerAwareTest {
  private boolean myDefaultEnableState;

  @Override
  public void setUp() throws Exception {
    myDefaultEnableState = CompilerReferenceService.IS_ENABLED_KEY.asBoolean();
    CompilerReferenceService.IS_ENABLED_KEY.setValue(true);
    super.setUp();

    CompilerReferenceService.getInstanceIfEnabled(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      CompilerReferenceService.IS_ENABLED_KEY.setValue(myDefaultEnableState);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
