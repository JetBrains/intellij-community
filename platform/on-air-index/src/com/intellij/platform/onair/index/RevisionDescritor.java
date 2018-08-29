// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.intellij.platform.onair.storage.api.Address;

import java.util.Map;

public class RevisionDescritor {
  public final int R;
  public final int baseR;

  public static class Heads {
    public final Address forwardIndexHead;
    public final Map<String, BTreeIndexStorage.AddressDescriptor> invertedIndicesHeads;
    public final Address vfsHead;

    public Heads(Address forwardIndexHead,
                 Map<String, BTreeIndexStorage.AddressDescriptor> invertedIndicesHeads, Address vfsHead) {
      this.forwardIndexHead = forwardIndexHead;
      this.invertedIndicesHeads = invertedIndicesHeads;
      this.vfsHead = vfsHead;
    }
  }

  public final Heads heads;

  public RevisionDescritor(int r, int baseR, Heads heads) {
    R = r;
    this.baseR = baseR;
    this.heads = heads;
  }

  public static RevisionDescritor fromRevision(String revision) {
    throw new UnsupportedOperationException();
    /*Map m = (Map)(((Map)indexHeads.get("inverted-indices")).get(indexId.getName()));
    final List<String> invertedAddress = (List<String>)m.get("inverted");
    final List<String> internaryAddress = (List<String>)m.get("internary");
    // final List<String> hashToVirtualFile = (List<String>)m.get("hash-to-file");
    Address internary = internaryAddress != null ? Address.fromStrings(internaryAddress) : null;
    address = new BTreeIndexStorage.AddressDescriptor(
      internary,
      Address.fromStrings(invertedAddress)*//*, Address.fromStrings(hashToVirtualFile)*//*
    );*/
  }
}
