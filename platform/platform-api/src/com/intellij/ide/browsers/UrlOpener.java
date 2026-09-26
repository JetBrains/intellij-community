// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class UrlOpener {
  public static final ExtensionPointName<UrlOpener> EP_NAME = ExtensionPointName.create("org.jetbrains.urlOpener");

  public abstract boolean openUrl(@NotNull WebBrowser browser, @NotNull String url, @Nullable Project project);
}
