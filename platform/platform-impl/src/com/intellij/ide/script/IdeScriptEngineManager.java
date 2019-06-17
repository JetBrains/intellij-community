// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.script;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class IdeScriptEngineManager {
  public static IdeScriptEngineManager getInstance() {
    return ServiceManager.getService(IdeScriptEngineManager.class);
  }

  @NotNull
  public abstract List<EngineInfo> getEngineInfos();

  @Nullable
  public abstract IdeScriptEngine getEngine(@NotNull EngineInfo engineInfo, @Nullable ClassLoader loader);

  @Nullable
  public abstract IdeScriptEngine getEngineByName(@NotNull String engineName, @Nullable ClassLoader loader);

  @Nullable
  public abstract IdeScriptEngine getEngineByFileExtension(@NotNull String extension, @Nullable ClassLoader loader);

  public abstract boolean isInitialized();

  public static class EngineInfo {
    public final String engineName;
    public final String engineVersion;
    public final String languageName;
    public final String languageVersion;
    public final List<String> fileExtensions;
    public final String factoryClass;
    public final PluginId pluginId;

    EngineInfo(@NotNull String engineName,
               @Nullable String engineVersion,
               @NotNull String languageName,
               @Nullable String languageVersion,
               @NotNull List<String> fileExtensions,
               @NotNull String factoryClass,
               @Nullable PluginId pluginId) {
      this.engineName = engineName;
      this.engineVersion = engineVersion;
      this.languageName = languageName;
      this.languageVersion = languageVersion;
      this.fileExtensions = fileExtensions;
      this.factoryClass = factoryClass;
      this.pluginId = pluginId;
    }
  }
}
