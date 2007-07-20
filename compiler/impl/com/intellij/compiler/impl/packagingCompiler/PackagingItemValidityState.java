/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.io.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public class PackagingItemValidityState implements ValidityState {
  private SmartList<Pair<String, Long>> myDestinations;

  public PackagingItemValidityState(List<DestinationInfo> destinationInfos) {
    myDestinations = new SmartList<Pair<String, Long>>();
    for (DestinationInfo info : destinationInfos) {
      final VirtualFile outputFile = info.getOutputFile();
      long timestamp = outputFile != null ? outputFile.getTimeStamp() : -1;
      myDestinations.add(Pair.create(info.getOutputPath(), timestamp));
    }
  }

  public PackagingItemValidityState(DataInputStream input) throws IOException {
    int size = input.readInt();
    myDestinations = new SmartList<Pair<String, Long>>();
    while (size-- > 0) {
      String path = IOUtil.readString(input);
      long timestamp = input.readLong();
      myDestinations.add(Pair.create(path, timestamp));
    }
  }

  public boolean equalsTo(final ValidityState otherState) {
    if (!(otherState instanceof PackagingItemValidityState)) {
      return false;
    }

    final SmartList<Pair<String, Long>> otherDestinations = ((PackagingItemValidityState)otherState).myDestinations;
    if (otherDestinations.size() != myDestinations.size()) {
      return false;
    }

    if (myDestinations.size() == 1) {
      return myDestinations.get(0).equals(otherDestinations.get(0));
    }

    return Comparing.haveEqualElements(myDestinations, otherDestinations);
  }


  public void save(final DataOutputStream output) throws IOException {
    output.writeInt(myDestinations.size());
    for (Pair<String, Long> pair : myDestinations) {
      IOUtil.writeString(pair.getFirst(), output);
      output.writeLong(pair.getSecond());
    }
  }
}
