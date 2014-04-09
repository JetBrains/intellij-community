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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;

public abstract class BrowserLauncher {
  public static BrowserLauncher getInstance() {
    return ServiceManager.getService(BrowserLauncher.class);
  }

  public abstract void open(@NotNull String url);

  public abstract void browse(@NotNull URI uri);

  public abstract void browse(@NotNull File file);

  public abstract void browse(@NotNull String url, @Nullable WebBrowser browser);

  public abstract void browse(@NotNull String url, @Nullable WebBrowser browser, @Nullable Project project);

  public abstract boolean browseUsingPath(@Nullable String url,
                                          @Nullable String browserPath,
                                          @Nullable WebBrowser browser,
                                          @Nullable Project project,
                                          @NotNull String[] additionalParameters);
}