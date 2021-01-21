// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.injected.editor;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class InjectionMeta {

  public final static Key<String> INJECTION_INDENT = Key.create("INJECTION_INDENT");

}
