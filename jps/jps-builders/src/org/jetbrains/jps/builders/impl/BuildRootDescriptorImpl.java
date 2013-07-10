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

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.File;

public class BuildRootDescriptorImpl extends BuildRootDescriptor {
  private final File myRoot;
  private final BuildTarget myTarget;
  private final boolean myCanUseFileCache;

  public BuildRootDescriptorImpl(BuildTarget target, File root) {
    this(target, root, false);
  }

  public BuildRootDescriptorImpl(BuildTarget target, File root, boolean canUseFileCache) {
    myTarget = target;
    myRoot = root;
    myCanUseFileCache = canUseFileCache;
  }

  @Override
  public String getRootId() {
    return FileUtilRt.toSystemIndependentName(myRoot.getAbsolutePath());
  }

  @Override
  public File getRootFile() {
    return myRoot;
  }

  @Override
  public BuildTarget<?> getTarget() {
    return myTarget;
  }

  @Override
  public boolean canUseFileCache() {
    return myCanUseFileCache;
  }
}
