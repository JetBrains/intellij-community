// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.flavor;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An interface intended to sink objects to mix them in a hash function.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface HashBuilder {
  @NotNull
  HashBuilder putInt(int val);

  @NotNull
  HashBuilder putBoolean(boolean val);

  /**
   * @param charSequence to mix in hash. Charset is managed by hash function.
   */
  @NotNull
  HashBuilder putString(@NotNull CharSequence charSequence);
}
