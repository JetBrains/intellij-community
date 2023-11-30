// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.UUID;

public abstract class WebBrowser {
  public abstract @NotNull @NlsSafe String getName();

  public abstract @NotNull UUID getId();

  public abstract @NotNull BrowserFamily getFamily();

  public abstract @NotNull Icon getIcon();

  public abstract @Nullable @NlsSafe String getPath();

  public abstract @NotNull String getBrowserNotFoundMessage();

  public abstract @Nullable BrowserSpecificSettings getSpecificSettings();

  public void addOpenUrlParameter(@NotNull List<? super String> command, @NotNull String url) {
    command.add(url);
  }
}