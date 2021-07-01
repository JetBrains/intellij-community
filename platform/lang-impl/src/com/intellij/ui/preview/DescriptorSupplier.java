// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.preview;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import org.jetbrains.annotations.Nullable;

public interface DescriptorSupplier {
  default @Nullable OpenFileDescriptor getDescriptor() {
    return null;
  }
}
