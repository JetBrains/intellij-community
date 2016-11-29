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
package org.jetbrains.jps.backwardRefs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BackwardReferenceIndexBuilder extends ModuleLevelBuilder {
  public BackwardReferenceIndexBuilder() {
    super(BuilderCategory.INITIAL);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "backward-references indexer";
  }

  @Override
  public void buildStarted(CompileContext context) {
    BackwardReferenceIndexWriter.initialize(context);
  }

  @Override
  public void buildFinished(CompileContext context) {
    BackwardReferenceIndexWriter.closeIfNeed();
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    if (dirtyFilesHolder.hasRemovedFiles()) {
      final BackwardReferenceIndexWriter writer = BackwardReferenceIndexWriter.getInstance();
      if (writer != null) {
        for (ModuleBuildTarget target : chunk.getTargets()) {
          final Collection<String> files = dirtyFilesHolder.getRemovedFiles(target);
          writer.processDeletedFiles(files);
        }
      }
    }
    return null;
  }
}
