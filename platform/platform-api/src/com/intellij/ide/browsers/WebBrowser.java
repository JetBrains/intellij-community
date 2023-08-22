package com.intellij.ide.browsers;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.UUID;

public abstract class WebBrowser {
  @NotNull
  public abstract @NlsSafe String getName();

  @NotNull
  public abstract UUID getId();

  @NotNull
  public abstract BrowserFamily getFamily();

  @NotNull
  public abstract Icon getIcon();

  @Nullable
  public abstract @NlsSafe String getPath();

  @NotNull
  public abstract String getBrowserNotFoundMessage();

  @Nullable
  public abstract BrowserSpecificSettings getSpecificSettings();

  public void addOpenUrlParameter(@NotNull List<? super String> command, @NotNull String url) {
    command.add(url);
  }
}