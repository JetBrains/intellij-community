// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FileSetDescriptorFactory {

  private FileSetDescriptorFactory() {
  }

  @Nullable
  public static FileSetDescriptor createDescriptor(@NotNull FileSetDescriptor.State state) {
    if (PatternDescriptor.PATTERN_TYPE.equals(state.type) && state.pattern != null) {
      return new PatternDescriptor(state.pattern);
    }
    if (NamedScopeDescriptor.NAMED_SCOPE_TYPE.equals(state.type) && state.name != null) {
      NamedScopeDescriptor descriptor = new NamedScopeDescriptor(state.name);
      if (state.pattern != null) {
        descriptor.setPattern(state.pattern);
      }
      return descriptor;
    }
    return null;
  }
}
