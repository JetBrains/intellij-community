/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
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
 *
 * @author nik
*/
class ChainedTargetsBuildListener implements BuildListener {
  private final CompileContextImpl myContext;

  public ChainedTargetsBuildListener(CompileContextImpl context) {
    myContext = context;
  }

  @Override
  public void filesGenerated(FileGeneratedEvent event) {
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
            fsState.markDirty(myContext, file, desc, pd.timestamps.getStorage(), false);
          }
          catch (IOException ignored) {
          }
        }
      }
    }
  }

  @Override
  public void filesDeleted(FileDeletedEvent event) {
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
