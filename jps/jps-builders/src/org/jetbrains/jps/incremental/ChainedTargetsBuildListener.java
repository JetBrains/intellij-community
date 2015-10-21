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
import org.jetbrains.jps.incremental.fs.BuildFSState;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

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
  public void filesGenerated(Collection<Pair<String, String>> paths) {
    BuildFSState fsState = myContext.getProjectDescriptor().fsState;
    BuildRootIndex rootsIndex = myContext.getProjectDescriptor().getBuildRootIndex();
    for (Pair<String, String> pair : paths) {
      String relativePath = pair.getSecond();
      File file = relativePath.equals(".") ? new File(pair.getFirst()) : new File(pair.getFirst(), relativePath);
      Collection<BuildRootDescriptor> descriptors = rootsIndex.findAllParentDescriptors(file, null, myContext);
      for (BuildRootDescriptor descriptor : descriptors) {
        try {
          fsState.markDirty(myContext, file, descriptor, myContext.getProjectDescriptor().timestamps.getStorage(), false);
        }
        catch (IOException ignored) {
        }
      }
    }
  }

  @Override
  public void filesDeleted(Collection<String> paths) {
    BuildFSState state = myContext.getProjectDescriptor().fsState;
    BuildRootIndex rootsIndex = myContext.getProjectDescriptor().getBuildRootIndex();
    for (String path : paths) {
      File file = new File(FileUtil.toSystemDependentName(path));
      Collection<BuildRootDescriptor> descriptors = rootsIndex.findAllParentDescriptors(file, null, myContext);
      for (BuildRootDescriptor descriptor : descriptors) {
        state.registerDeleted(descriptor.getTarget(), file);
      }
    }
  }
}
