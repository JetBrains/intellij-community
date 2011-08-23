/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
* @author nik
*/
public abstract class RootDetector {
  private OrderRootType myRootType;
  private boolean myJarDirectory;
  private String myPresentableRootTypeName;

  protected RootDetector(OrderRootType rootType, boolean jarDirectory, String presentableRootTypeName) {
    myRootType = rootType;
    myJarDirectory = jarDirectory;
    myPresentableRootTypeName = presentableRootTypeName;
  }

  public boolean isJarDirectory() {
    return myJarDirectory;
  }

  public OrderRootType getRootType() {
    return myRootType;
  }

  public String getPresentableRootTypeName() {
    return myPresentableRootTypeName;
  }

  @NotNull
  public abstract Collection<VirtualFile> detectRoots(@NotNull VirtualFile rootCandidate, @NotNull ProgressIndicator progressIndicator);
}
