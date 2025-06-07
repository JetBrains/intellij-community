// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

public final class JavaRecordComponentNameIndex extends StringStubIndexExtension<PsiRecordComponent> {
  private static final JavaRecordComponentNameIndex ourInstance = new JavaRecordComponentNameIndex();

  public static JavaRecordComponentNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull StubIndexKey<String, PsiRecordComponent> getKey() {
    return JavaStubIndexKeys.RECORD_COMPONENTS;
  }
}