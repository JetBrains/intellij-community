/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Map;

/**
 * @author nik
 */
public abstract class JpsEncodingConfigurationService {
  public static JpsEncodingConfigurationService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsEncodingConfigurationService.class);
  }

  @Nullable
  public abstract String getGlobalEncoding(@NotNull JpsGlobal global);

  public abstract void setGlobalEncoding(@NotNull JpsGlobal global, @Nullable String encoding);

  @Nullable
  public abstract String getProjectEncoding(@NotNull JpsModel model);

  @Nullable
  public abstract JpsEncodingProjectConfiguration getEncodingConfiguration(@NotNull JpsProject project);

  @NotNull
  public abstract JpsEncodingProjectConfiguration setEncodingConfiguration(@NotNull JpsProject project, @Nullable String projectEncoding,
                                                                           @NotNull Map<String, String> urlToEncoding);
}
