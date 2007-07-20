/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.FileProcessingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class PackagingProcessingItem implements FileProcessingCompiler.ProcessingItem {
  private VirtualFile mySourceFile;
  private List<DestinationInfo> myDestinations = new ArrayList<DestinationInfo>();

  public PackagingProcessingItem(final VirtualFile sourceFile) {
    mySourceFile = sourceFile;
  }

  @NotNull
  public VirtualFile getFile() {
    return mySourceFile;
  }

  public void addDestination(DestinationInfo info) {
    myDestinations.add(info);
  }

  public List<DestinationInfo> getDestinations() {
    return myDestinations;
  }

  @Nullable
  public ValidityState getValidityState() {
    return new PackagingItemValidityState(myDestinations);
  }
}
