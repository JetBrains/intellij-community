// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
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

public class JavaBackwardReferenceIndexBuilder extends ModuleLevelBuilder {
  public static final String BUILDER_ID = "compiler.ref.index";
  private static final String MESSAGE_TYPE = "processed module";
  private final Set<ModuleBuildTarget> myCompiledTargets = ContainerUtil.newConcurrentSet();
  private int myAttempt = 0;

  public JavaBackwardReferenceIndexBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "backward-references indexer";
  }

  @Override
  public void buildStarted(CompileContext context) {
    JavaBackwardReferenceIndexWriter.initialize(context, myAttempt++);
  }

  @Override
  public void buildFinished(CompileContext context) {
    if (JavaBackwardReferenceIndexWriter.getInstance() != null) {
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

    JavaBackwardReferenceIndexWriter.closeIfNeed(false);
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
    final JavaBackwardReferenceIndexWriter writer = JavaBackwardReferenceIndexWriter.getInstance();
    if (writer != null) {
      final Throwable cause = writer.getRebuildRequestCause();
      if (cause != null) {
        JavaBackwardReferenceIndexWriter.closeIfNeed(true);
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
