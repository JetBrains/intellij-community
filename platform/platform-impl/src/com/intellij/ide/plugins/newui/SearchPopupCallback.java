// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public abstract class SearchPopupCallback implements Consumer<String> {
  public String prefix;

  public SearchPopupCallback(@Nullable String prefix) {
    this.prefix = prefix;
  }
}