/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Represents a description of a module which may be either loaded into the project or unloaded. Use this class only if you need to process
 * all modules including unloaded, in other cases {@link Module} should be used instead.
 *
 * @see UnloadedModuleDescription
 * @see LoadedModuleDescription
 */
@ApiStatus.Experimental
public interface ModuleDescription {
  @NotNull @NlsSafe
  String getName();

  /**
   * Names of the modules on which the current module depend.
   */
  @NotNull @Unmodifiable
  List<@NlsSafe String> getDependencyModuleNames();
}
