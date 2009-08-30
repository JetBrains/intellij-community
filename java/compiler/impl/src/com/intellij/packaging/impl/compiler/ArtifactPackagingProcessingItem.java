/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.DestinationInfo;
import com.intellij.compiler.impl.FileProcessingCompilerStateCache;
import com.intellij.openapi.compiler.FileProcessingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

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
      myEnabledDestinations = new ArrayList<DestinationInfo>();
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