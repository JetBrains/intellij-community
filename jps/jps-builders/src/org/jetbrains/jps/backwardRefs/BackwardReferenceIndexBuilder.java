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
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.backwardRefs.JavacReferenceIndexWriter;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BackwardReferenceIndexBuilder extends ModuleLevelBuilder {
  public static final String BUILDER_ID = "compiler.ref.index";
  private static final String MESSAGE_TYPE = "processed module";
  private final Set<ModuleBuildTarget> myCompiledTargets = ContainerUtil.newConcurrentSet();
  private int myAttempt = 0;

  public BackwardReferenceIndexBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "backward-references indexer";
  }

  @Override
  public void buildStarted(CompileContext context) {
    JpsJavacReferenceIndexWriterHolder.initialize(context, myAttempt++);
  }

  @Override
  public void buildFinished(CompileContext context) {
    if (JpsJavacReferenceIndexWriterHolder.getInstance() != null) {
      final BuildTargetIndex targetIndex = context.getProjectDescriptor().getBuildTargetIndex();
      for (JpsModule module : context.getProjectDescriptor().getProject().getModules()) {
        boolean allAreDummyOrCompiled = true;
        for (ModuleBasedTarget<?> target : targetIndex.getModuleBasedTargets(module, BuildTargetRegistry.ModuleTargetSelector.ALL)) {
          if (target instanceof ModuleBuildTarget && !myCompiledTargets.contains(target) && !targetIndex.isDummy(target)) {
            allAreDummyOrCompiled = false;
          }
        }
        if (allAreDummyOrCompiled) {
          context.processMessage(new CustomBuilderMessage(BUILDER_ID, MESSAGE_TYPE, module.getName()));
        }
      }
      myCompiledTargets.clear();
    }

    JpsJavacReferenceIndexWriterHolder.closeIfNeed(false);
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
    final JavacReferenceIndexWriter writer = JpsJavacReferenceIndexWriterHolder.getInstance();
    if (writer != null) {
      final Exception cause = writer.getRebuildRequestCause();
      if (cause != null) {
        JpsJavacReferenceIndexWriterHolder.closeIfNeed(true);
      }

      if (dirtyFilesHolder.hasRemovedFiles()) {
        for (ModuleBuildTarget target : chunk.getTargets()) {
          final Collection<String> files = dirtyFilesHolder.getRemovedFiles(target);
          writer.processDeletedFiles(files);
        }
      }

      for (ModuleBuildTarget target : chunk.getTargets()) {
        if (context.getScope().isWholeTargetAffected(target)) {
          myCompiledTargets.add(target);
        }
      }
    }
    return null;
  }
}
