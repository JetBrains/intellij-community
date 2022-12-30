// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarFinder;

import org.jetbrains.annotations.NotNull;

/**
 * @author Edoardo Luppi
 */
public class SourceSearchException extends Exception {
  public SourceSearchException(@NotNull final String message) {
    super(message);
  }
}
