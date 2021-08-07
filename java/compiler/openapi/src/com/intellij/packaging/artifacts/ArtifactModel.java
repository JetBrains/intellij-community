/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.packaging.artifacts;

import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface ArtifactModel {
  @RequiresReadLock
  Artifact @NotNull [] getArtifacts();

  @Nullable
  @RequiresReadLock
  Artifact findArtifact(@NotNull String name);

  @NotNull
  Artifact getArtifactByOriginal(@NotNull Artifact artifact);

  @NotNull
  Artifact getOriginalArtifact(@NotNull Artifact artifact);

  @NotNull
  @RequiresReadLock
  Collection<? extends Artifact> getArtifactsByType(@NotNull ArtifactType type);

  @RequiresReadLock
  List<? extends Artifact> getAllArtifactsIncludingInvalid();
}
