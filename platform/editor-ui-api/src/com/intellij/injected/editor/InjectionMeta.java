// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.injected.editor;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public final class InjectionMeta {

  private static final Key<String> INJECTION_INDENT = Key.create("INJECTION_INDENT");

  public static Key<String> getInjectionIndent() {
    return INJECTION_INDENT;
  }

  private InjectionMeta() {
  }
}
