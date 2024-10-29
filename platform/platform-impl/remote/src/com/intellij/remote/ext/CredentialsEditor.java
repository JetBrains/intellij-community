// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.remote.RemoteSdkAdditionalData;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface CredentialsEditor<T> {
  /**
   * The user-visible name of the editor.
   * <p>
   * It will be used as the label of the option corresponding to this editor in
   * the outer form.
   *
   * @return the name of the editor
   */
  @NotNull
  @NlsContexts.Label String getName();

  JPanel getMainPanel();

  void onSelected();

  ValidationInfo validate();

  @NlsContexts.DialogMessage
  String validateFinal(@NotNull Supplier<? extends RemoteSdkAdditionalData> supplier,
                       @NotNull Consumer<? super String> helpersPathUpdateCallback);

  void saveCredentials(T credentials);

  void init(T credentials);
}
