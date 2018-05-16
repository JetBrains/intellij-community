// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder

internal val DO_NOT_MARK_DIRTY_SUFFIX = "___DoNotMarkDirty"

class SkipMarkingSomeFilesAsDirtyBuilder : ModuleLevelBuilder(BuilderCategory.SOURCE_PROCESSOR) {
  override fun getPresentableName() = "Skip marking some files as dirty"

  override fun build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
                     outputConsumer: OutputConsumer): ExitCode {
    //need to call this to set JavaBuilderUtil.MAPPINGS_DELTA_KEY key otherwise JavaBuilderUtil.updateMappingsOnRoundCompletion won't to anything
    JavaBuilderUtil.getDependenciesRegistrar(context)

    JavaBuilderUtil.registerFilterToSkipMarkingAffectedFileDirty(context, { it.nameWithoutExtension.endsWith(DO_NOT_MARK_DIRTY_SUFFIX)})
    return ExitCode.OK
  }
}