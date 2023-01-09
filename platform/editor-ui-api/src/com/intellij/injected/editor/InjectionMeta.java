// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.injected.editor;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public final class InjectionMeta {

  public final static Key<String> INJECTION_INDENT = Key.create("INJECTION_INDENT");

}
