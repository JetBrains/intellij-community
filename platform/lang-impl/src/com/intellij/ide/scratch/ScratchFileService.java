/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class ScratchFileService {

  public static final RootId SCRATCHES = new RootId("scratches", "Scratches");

  public static ScratchFileService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ScratchFileService.class);
  }

  public static ScratchFileService getInstance() {
    return ServiceManager.getService(ScratchFileService.class);
  }

  @NotNull
  public abstract String getRootPath(@NotNull RootId rootId);

  public abstract boolean isFileInRoot(@NotNull VirtualFile file, @NotNull RootId rootId);

  @Nullable
  public abstract VirtualFile getOrCreateFile(@NotNull RootId rootId, @NotNull String pathName) throws IOException;

  @Nullable
  public abstract VirtualFile createScratchFile(@NotNull Project project, @NotNull Language language, @NotNull String initialContent);

  @NotNull
  public abstract PerFileMappings<Language> getScratchesMapping();

  public static class RootId {
    private static final Map<String, RootId> ourInstances = ContainerUtil.newLinkedHashMap();

    private final String myId;
    private final String myDisplayName;

    public RootId(@NotNull String id, @Nullable String displayName) {
      myId = id;
      myDisplayName = displayName;
      synchronized (ourInstances) {
        RootId prev = ourInstances.put(id, this);
        if (prev != null) {
          throw new AssertionError(String.format("rootType '%s' already registered", id));
        }
      }
    }

    @NotNull
    public final String getId() {
      return myId;
    }

    @Nullable
    public final String getDisplayName() {
      return myDisplayName;
    }

    public boolean isHidden() {
      return myDisplayName == null;
    }

    public static synchronized List<RootId> getAllRootIds() {
      synchronized (ourInstances) {
        return ContainerUtil.newArrayList(ourInstances.values());
      }
    }
  }
}
