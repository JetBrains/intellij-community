/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.*;
import com.intellij.compiler.impl.packagingCompiler.*;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * @author nik
 */
public class ArtifactPackagingProcessingItem implements FileProcessingCompiler.ProcessingItem {
  private final VirtualFile mySourceFile;
  private final List<Pair<DestinationInfo, Boolean>> myDestinations = new SmartList<Pair<DestinationInfo, Boolean>>();
  private List<DestinationInfo> myEnabledDestinations;
  private boolean mySourceFileModified;
  private ArtifactPackagingItemValidityState myOldState;

  public ArtifactPackagingProcessingItem(final VirtualFile sourceFile) {
    mySourceFile = sourceFile;
  }

  @NotNull
  public VirtualFile getFile() {
    return mySourceFile;
  }

  public void addDestination(DestinationInfo info, boolean enabled) {
    for (int i = 0; i < myDestinations.size(); i++) {
      Pair<DestinationInfo, Boolean> pair = myDestinations.get(i);
      if (info.getOutputPath().equals(pair.getFirst().getOutputPath())) {
        if (enabled && !pair.getSecond()) {
          myDestinations.set(i, Pair.create(info, true));
        }
        return;
      }
    }
    myDestinations.add(Pair.create(info, enabled));
  }

  public List<Pair<DestinationInfo, Boolean>> getDestinations() {
    return myDestinations;
  }

  public void init(FileProcessingCompilerStateCache cache) throws IOException {
    final String url = mySourceFile.getUrl();
    myOldState = (ArtifactPackagingItemValidityState)cache.getExtState(url);
    mySourceFileModified = cache.getTimestamp(url) != mySourceFile.getTimeStamp();
  }

  public void setProcessed() {
    for (DestinationInfo destination : myEnabledDestinations) {
      destination.update();
    }
  }

  public List<DestinationInfo> getEnabledDestinations() {
    if (myEnabledDestinations == null) {
      myEnabledDestinations = new SmartList<DestinationInfo>();
      for (Pair<DestinationInfo, Boolean> destination : myDestinations) {
        if (destination.getSecond()) {
          myEnabledDestinations.add(destination.getFirst());
        }
      }
    }
    return myEnabledDestinations;
  }

  @Nullable
  public ValidityState getValidityState() {
    return new ArtifactPackagingItemValidityState(getEnabledDestinations(), mySourceFileModified, myOldState);
  }
}
