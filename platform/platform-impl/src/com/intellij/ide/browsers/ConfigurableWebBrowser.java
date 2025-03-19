// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.UUID;

final class ConfigurableWebBrowser extends WebBrowser {
  private final UUID id;
  private @NotNull BrowserFamily family;
  private @NotNull String name;
  private boolean active;
  private String path;

  private BrowserSpecificSettings specificSettings;

  @SuppressWarnings("UnusedDeclaration")
  ConfigurableWebBrowser() {
    this(UUID.randomUUID(), BrowserFamily.CHROME);
  }

  ConfigurableWebBrowser(@NotNull UUID id, @NotNull BrowserFamily family) {
    this(id, family, family.getName(), family.getExecutionPath(), true, family.createBrowserSpecificSettings());
  }

  ConfigurableWebBrowser(@NotNull UUID id,
                                @NotNull BrowserFamily family,
                                @NotNull String name,
                                @Nullable String path,
                                boolean active,
                                @Nullable BrowserSpecificSettings specificSettings) {
    this.id = id;
    this.family = family;
    this.name = name;

    this.path = StringUtil.nullize(path);
    this.active = active;
    this.specificSettings = specificSettings;
  }

  public void setName(@NotNull String value) {
    name = value;
  }

  public void setFamily(@NotNull BrowserFamily value) {
    family = value;
  }

  @Override
  public @NotNull Icon getIcon() {
    if (family == BrowserFamily.CHROME) {
      if (WebBrowserManager.isYandexBrowser(this)) {
        return AllIcons.Xml.Browsers.Yandex;
      }
      else if (checkNameAndPath("Dartium") || checkNameAndPath("Chromium")) {
        return AllIcons.Xml.Browsers.Chromium;
      }
      else if (checkNameAndPath("Canary")) {
        return AllIcons.Xml.Browsers.Canary;
      }
      else if (WebBrowserManager.isOpera(this)) {
        return AllIcons.Xml.Browsers.Opera;
      }
      else if (checkNameAndPath("node-webkit") || checkNameAndPath("nw") || checkNameAndPath("nwjs")) {
        return AllIcons.Xml.Browsers.Nwjs;
      }
      else if (WebBrowserManager.isEdge(this)) {
        return AllIcons.Xml.Browsers.Edge;
      }
    }
    else if (family == BrowserFamily.FIREFOX) {
      if (checkNameAndPath("Dev")) {
        return AllIcons.Xml.Browsers.FirefoxDeveloper;
      }
    }

    return family.getIcon();
  }

  private boolean checkNameAndPath(@NotNull String what) {
    return WebBrowserManager.checkNameAndPath(what, this);
  }

  @Override
  public @Nullable String getPath() {
    return path;
  }

  public void setPath(@Nullable String value) {
    path = PathUtil.toSystemIndependentName(StringUtil.nullize(value));
  }

  @Override
  public @Nullable BrowserSpecificSettings getSpecificSettings() {
    return specificSettings;
  }

  public void setSpecificSettings(@Nullable BrowserSpecificSettings value) {
    specificSettings = value;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConfigurableWebBrowser browser)) {
      return false;
    }

    return getId().equals(browser.getId()) &&
           family.equals(browser.family) &&
           active == browser.active &&
           Comparing.strEqual(name, browser.name) &&
           Objects.equals(path, browser.path) &&
           Comparing.equal(specificSettings, browser.specificSettings);
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public @NotNull UUID getId() {
    return id;
  }

  @Override
  public @NotNull BrowserFamily getFamily() {
    return family;
  }

  @Override
  public @NotNull String getBrowserNotFoundMessage() {
    return IdeBundle.message("error.0.browser.path.not.specified", getName());
  }


  @Override
  public String toString() {
    return getName() + " (" + getPath() + ")";
  }
}