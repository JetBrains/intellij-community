// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.preview;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @deprecated see {@link PreviewHintProvider} deprecation notice
 */
@Deprecated
public interface PreviewHintComponent {
  @TestOnly
  boolean isEqualTo(@Nullable PreviewHintComponent other);
}
