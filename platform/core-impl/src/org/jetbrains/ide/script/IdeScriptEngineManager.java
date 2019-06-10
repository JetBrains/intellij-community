/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.ide.script;

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
