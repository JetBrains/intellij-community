/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public interface SdkModificator {
  String getName();

  void setName(String name);

  String getHomePath();

  void setHomePath(String path);

  @Nullable
  String getVersionString();

  void setVersionString(String versionString);

  SdkAdditionalData getSdkAdditionalData();

  void setSdkAdditionalData(SdkAdditionalData data);

  // todo: replace ProjectRootType with OrderRootType?
  VirtualFile[] getRoots(ProjectRootType rootType);

  void addRoot(VirtualFile root, ProjectRootType rootType);

  void removeRoot(VirtualFile root, ProjectRootType rootType);

  void removeRoots(ProjectRootType rootType);

  void removeAllRoots();

  void commitChanges();

  boolean isWritable();
}
