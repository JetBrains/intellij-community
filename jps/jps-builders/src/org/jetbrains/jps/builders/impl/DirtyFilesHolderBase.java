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
package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author nik
 */
public abstract class DirtyFilesHolderBase<R extends BuildRootDescriptor, T extends BuildTarget<R>> implements DirtyFilesHolder<R, T> {
  protected final CompileContext myContext;

  public DirtyFilesHolderBase(CompileContext context) {
    myContext = context;
  }

  @Override
  public boolean hasDirtyFiles() throws IOException {
    final Ref<Boolean> hasDirtyFiles = Ref.create(false);
    processDirtyFiles(new FileProcessor<R, T>() {
      @Override
      public boolean apply(T target, File file, R root) throws IOException {
        hasDirtyFiles.set(true);
        return false;
      }
    });
    return hasDirtyFiles.get();
  }

  @Override
  public boolean hasRemovedFiles() {
    Map<BuildTarget<?>, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(myContext);
    return map != null && !map.isEmpty();
  }

  @NotNull
  @Override
  public Collection<String> getRemovedFiles(@NotNull T target) {
    Map<BuildTarget<?>, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(myContext);
    if (map != null) {
      Collection<String> paths = map.get(target);
      if (paths != null) {
        return paths;
      }
    }
    return Collections.emptyList();
  }
}
