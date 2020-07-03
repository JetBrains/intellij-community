// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class ConverterProvider {
  public static final ExtensionPointName<ConverterProvider> EP_NAME = new ExtensionPointName<>("com.intellij.project.converterProvider");
  private final String myId;

  protected ConverterProvider(@NotNull @NonNls String id) {
    myId = id;
  }

  public final String getId() {
    return myId;
  }

  @NlsContexts.DialogMessage
  @NotNull
  public abstract String getConversionDescription();

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  public abstract ProjectConverter createConverter(@NotNull ConversionContext context);
}
