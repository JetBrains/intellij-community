/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.browsers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public abstract class UrlOpener {
  public static final ExtensionPointName<UrlOpener> EP_NAME = ExtensionPointName.create("org.jetbrains.urlOpener");

  @Deprecated
  /**
   * @deprecated Use {@link com.intellij.ide.browsers.BrowserLauncher#browse(String, WebBrowser)}
   */
  public static void launchBrowser(@NotNull String url, @Nullable WebBrowser browser) {
    BrowserLauncher.getInstance().browse(url, browser);
  }

  @Deprecated
  /**
   * @deprecated Use {@link com.intellij.ide.browsers.BrowserLauncher#browse(String, WebBrowser, com.intellij.openapi.project.Project)}
   */
  public static void launchBrowser(@NotNull String url, @Nullable WebBrowser browser, @Nullable Project project) {
    BrowserLauncher.getInstance().browse(url, browser, project);
  }

  public abstract boolean openUrl(@NotNull WebBrowser browser, @NotNull String url, @Nullable Project project);
}

