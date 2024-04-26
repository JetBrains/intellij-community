// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;

import java.io.File;
import java.io.IOException;

/**
 * Notifies targets about changes in their sources made by other builders
 */
final class ChainedTargetsBuildListener implements BuildListener {
  private final CompileContextImpl myContext;

  ChainedTargetsBuildListener(CompileContextImpl context) {
    myContext = context;
  }

  @Override
  public void filesGenerated(@NotNull FileGeneratedEvent event) {
    final ProjectDescriptor pd = myContext.getProjectDescriptor();
    final BuildFSState fsState = pd.fsState;
    for (Pair<String, String> pair : event.getPaths()) {
      final String relativePath = pair.getSecond();
      final File file = relativePath.equals(".") ? new File(pair.getFirst()) : new File(pair.getFirst(), relativePath);
      for (BuildRootDescriptor desc : pd.getBuildRootIndex().findAllParentDescriptors(file, myContext)) {
        if (!event.getSourceTarget().equals(desc.getTarget())) {
          // do not mark files belonging to the target that originated the event
          // It is assumed that those files will be explicitly marked dirty by particular builder, if needed.
          try {
            fsState.markDirty(myContext, file, desc, pd.getProjectStamps().getStampStorage(), false);
          }
          catch (IOException ignored) {
          }
        }
      }
    }
  }

  @Override
  public void filesDeleted(@NotNull FileDeletedEvent event) {
    final BuildFSState state = myContext.getProjectDescriptor().fsState;
    final BuildRootIndex rootsIndex = myContext.getProjectDescriptor().getBuildRootIndex();
    for (String path : event.getFilePaths()) {
      final File file = new File(FileUtil.toSystemDependentName(path));
      for (BuildRootDescriptor desc : rootsIndex.findAllParentDescriptors(file, myContext)) {
        state.registerDeleted(myContext, desc.getTarget(), file);
      }
    }
  }
}
