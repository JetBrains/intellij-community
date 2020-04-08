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

import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a module which is unloaded from the project. Such modules aren't shown in UI (except for a special 'Load/Unload Modules' dialog),
 * all of their contents is excluded from the project so it isn't indexed or compiled.
 */
@ApiStatus.Experimental
public interface UnloadedModuleDescription extends ModuleDescription {
  @NotNull
  List<VirtualFilePointer> getContentRoots();

  @NotNull
  List<String> getGroupPath();
}
