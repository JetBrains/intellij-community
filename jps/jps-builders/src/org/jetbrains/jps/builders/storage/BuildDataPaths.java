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
package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.File;

public interface BuildDataPaths {
  File getDataStorageRoot();

  File getTargetsDataRoot();

  File getTargetTypeDataRoot(BuildTargetType<?> targetType);

  File getTargetDataRoot(BuildTarget<?> target);

  @NotNull
  File getTargetDataRoot(@NotNull BuildTargetType<?> targetType, @NotNull String targetId);
}
