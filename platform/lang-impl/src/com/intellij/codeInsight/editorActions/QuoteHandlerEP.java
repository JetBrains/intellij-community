// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers {@link QuoteHandler} for given file type.
 */
@ApiStatus.Internal
public final class QuoteHandlerEP extends BaseKeyedLazyInstance<QuoteHandler> implements KeyedLazyInstance<QuoteHandler> {
  public static final ExtensionPointName<KeyedLazyInstance<QuoteHandler>> EP_NAME = new ExtensionPointName<>("com.intellij.quoteHandler");

  // these must be public for scrambling compatibility
  @Attribute("fileType")
  @RequiredElement
  public String fileType;

  @Attribute("className")
  @RequiredElement
  public String className;

  @Override
  public @NotNull String getKey() {
    return fileType;
  }

  @Override
  protected @Nullable String getImplementationClassName() {
    return className;
  }
}