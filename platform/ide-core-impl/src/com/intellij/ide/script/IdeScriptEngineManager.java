// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.script;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class IdeScriptEngineManager {

  public static IdeScriptEngineManager getInstance() {
    return ApplicationManager.getApplication().getService(IdeScriptEngineManager.class);
  }

  @NotNull
  public abstract List<EngineInfo> getEngineInfos();

  @Nullable
  public abstract IdeScriptEngine getEngine(@NotNull EngineInfo engineInfo, @Nullable ClassLoader loader);

  @Nullable
  public abstract IdeScriptEngine getEngineByName(@NotNull @NonNls String engineName, @Nullable ClassLoader loader);

  @Nullable
  public abstract IdeScriptEngine getEngineByFileExtension(@NotNull String extension, @Nullable ClassLoader loader);

  public static class EngineInfo {
    public final @NonNls String engineName;
    public final String engineVersion;
    public final String languageName;
    public final String languageVersion;
    public final List<String> fileExtensions;
    public final String factoryClass;
    public final PluginDescriptor plugin;

    EngineInfo(@NotNull @NonNls String engineName,
               @Nullable String engineVersion,
               @NotNull String languageName,
               @Nullable String languageVersion,
               @NotNull List<String> fileExtensions,
               @NotNull String factoryClass,
               @Nullable PluginDescriptor plugin) {
      this.engineName = engineName;
      this.engineVersion = engineVersion;
      this.languageName = languageName;
      this.languageVersion = languageVersion;
      this.fileExtensions = fileExtensions;
      this.factoryClass = factoryClass;
      this.plugin = plugin;
    }
  }
}
