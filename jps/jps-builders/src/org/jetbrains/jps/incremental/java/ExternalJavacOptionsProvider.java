/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.java;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaCompilingTool;

import java.util.Collection;
import java.util.Collections;

/**
 * An extension for setting up additional VM options for external java compiler.
 */
public interface ExternalJavacOptionsProvider {
  /**
   * @deprecated Use {@link #getOptions(JavaCompilingTool, int)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @NotNull
  default Collection<String> getOptions(@NotNull JavaCompilingTool tool) {
    return Collections.emptyList();
  }

  @NotNull
  default Collection<String> getOptions(@NotNull JavaCompilingTool tool, int compilerSdkVersion) {
    return getOptions(tool);
  }
}

