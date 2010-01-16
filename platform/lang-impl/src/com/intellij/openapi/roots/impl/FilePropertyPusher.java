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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Gregory.Shrago
 */
public interface FilePropertyPusher<T> {
  ExtensionPointName<FilePropertyPusher> EP_NAME = ExtensionPointName.create("com.intellij.filePropertyPusher");

  void initExtra(Project project, MessageBus bus, Engine languageLevelUpdater);
  @NotNull
  Key<T> getFileDataKey();
  boolean pushDirectoriesOnly();

  T getDefaultValue();

  @Nullable
  T getImmediateValue(Project project, VirtualFile file);

  @Nullable
  T getImmediateValue(Module module);

  boolean acceptsFile(VirtualFile file);

  void persistAttribute(VirtualFile fileOrDir, T value) throws IOException;

  public interface Engine {
    void pushAll();
    void pushRecursively(final VirtualFile vile, final Project project);
  }

  void afterRootsChanged(Project project);
}
