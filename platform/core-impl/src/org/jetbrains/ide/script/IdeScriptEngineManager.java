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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class IdeScriptEngineManager {
  public static IdeScriptEngineManager getInstance() {
    return ServiceManager.getService(IdeScriptEngineManager.class);
  }

  @NotNull
  public abstract List<String> getLanguages();

  @NotNull
  public abstract List<String> getFileExtensions(@Nullable String language);

  @Nullable
  public abstract IdeScriptEngine getEngineForLanguage(@NotNull String language);

  @Nullable
  public abstract IdeScriptEngine getEngineForFileExtension(@NotNull String extension);

  public abstract boolean isInitialized();
}
