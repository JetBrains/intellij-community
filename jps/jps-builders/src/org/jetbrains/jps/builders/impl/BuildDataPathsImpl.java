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

import com.intellij.util.PathUtilRt;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;

import java.io.File;

/**
 * @author nik
 */
public class BuildDataPathsImpl implements BuildDataPaths {
  private final File myDataStorageRoot;

  public BuildDataPathsImpl(File dataStorageRoot) {
    myDataStorageRoot = dataStorageRoot;
  }

  @Override
  public File getDataStorageRoot() {
    return myDataStorageRoot;
  }

  @Override
  public File getTargetsDataRoot() {
    return new File(myDataStorageRoot, "targets");
  }

  @Override
  public File getTargetTypeDataRoot(BuildTargetType<?> targetType) {
    return new File(getTargetsDataRoot(), targetType.getTypeId());
  }

  @Override
  public File getTargetDataRoot(BuildTarget<?> target) {
    final String targetId = target.getId();
    // targetId may diff from another targetId only in case
    // when used as a file name in case-insensitive file systems, both paths for different targets will point to the same dir
    return new File(getTargetTypeDataRoot(target.getTargetType()), PathUtilRt.suggestFileName(targetId + "_" + Integer.toHexString(targetId.hashCode()), true, false));
  }
}
