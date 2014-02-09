package com.intellij.ide.browsers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.UUID;

public abstract class WebBrowser {
  @NotNull
  public abstract String getName();

  @NotNull
  public abstract UUID getId();

  @NotNull
  public abstract BrowserFamily getFamily();

  @NotNull
  public abstract Icon getIcon();

  @Nullable
  public abstract String getPath();

  @NotNull
  public abstract String getBrowserNotFoundMessage();

  @Nullable
  public abstract BrowserSpecificSettings getSpecificSettings();
}