// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.util.indexing.provided.SharedIndexChunkLocator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DummySharedIndexLocator implements SharedIndexChunkLocator {
  final static Map<Project, Integer> ourRequestCount = Collections.synchronizedMap(new WeakHashMap<>());

  @Override
  public List<ChunkDescriptor> locateIndex(@NotNull Project project,
                                           @NotNull Collection<? extends OrderEntry> entries,
                                           @NotNull ProgressIndicator indicator) {
    ourRequestCount.compute(project, (__, oldValue) -> oldValue == null ? 1 : (oldValue + 1));
    return Collections.emptyList();
  }
}
