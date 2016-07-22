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

package com.intellij.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.List;

/**
 * // TODO: merge with FileReferenceHelper & drop
 *
 * @author spleaner
 * @deprecated use {@link com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper} instead
 */
@Deprecated
public abstract class LogicalRootsManager {

  public static final Topic<LogicalRootListener> LOGICAL_ROOTS = new Topic<>("logical root changes", LogicalRootListener.class);

  public static LogicalRootsManager getLogicalRootsManager(@NotNull final Project project) {
    return ServiceManager.getService(project, LogicalRootsManager.class);
  }

  @Nullable
  public abstract LogicalRoot findLogicalRoot(@NotNull final VirtualFile file);

  public abstract List<LogicalRoot> getLogicalRoots();

  public abstract List<LogicalRoot> getLogicalRoots(@NotNull final Module module);

  public abstract List<LogicalRoot> getLogicalRootsOfType(@NotNull final Module module, @NotNull final LogicalRootType... types);

  public abstract <T extends LogicalRoot> List<T> getLogicalRootsOfType(@NotNull final Module module, @NotNull final LogicalRootType<T> type);

  @NotNull
  public abstract LogicalRootType[] getRootTypes(@NotNull final FileType type);

  public abstract void registerRootType(@NotNull final FileType fileType, @NotNull final LogicalRootType... rootTypes);

  public abstract <T extends LogicalRoot> void registerLogicalRootProvider(@NotNull final LogicalRootType<T> rootType, @NotNull NotNullFunction<Module,List<T>> provider);

  public interface LogicalRootListener extends EventListener {
    void logicalRootsChanged();
  }
}
