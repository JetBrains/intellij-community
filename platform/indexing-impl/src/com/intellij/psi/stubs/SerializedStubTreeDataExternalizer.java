// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.util.CompressionUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentHashMapValueStorage;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

public class SerializedStubTreeDataExternalizer implements DataExternalizer<SerializedStubTree> {
  @NotNull
  private final SerializationManagerEx mySerializationManager;
  private final StubForwardIndexExternalizer<?> myStubIndexesExternalizer;
  private final boolean mySaveIndexingStamp;

  public SerializedStubTreeDataExternalizer(@NotNull SerializationManagerEx manager,
                                            @NotNull StubForwardIndexExternalizer<?> externalizer,
                                            boolean saveIndexingStamp) {
    mySerializationManager = manager;
    myStubIndexesExternalizer = externalizer;
    mySaveIndexingStamp = saveIndexingStamp;
  }

  @Override
  public final void save(@NotNull final DataOutput out, @NotNull final SerializedStubTree tree) throws IOException {
    if (PersistentHashMapValueStorage.COMPRESSION_ENABLED) {
      DataInputOutputUtil.writeINT(out, tree.myTreeByteLength);
      out.write(tree.myTreeBytes, 0, tree.myTreeByteLength);
      DataInputOutputUtil.writeINT(out, tree.myIndexedStubByteLength);
      out.write(tree.myIndexedStubBytes, 0, tree.myIndexedStubByteLength);
    }
    else {
      CompressionUtil.writeCompressed(out, tree.myTreeBytes, 0, tree.myTreeByteLength);
      CompressionUtil.writeCompressed(out, tree.myIndexedStubBytes, 0, tree.myIndexedStubByteLength);
    }
    IndexingStampInfo indexingStampInfo = tree.getIndexingStampInfo();
    if (indexingStampInfo != null && mySaveIndexingStamp) {
      indexingStampInfo.save(out);
    }
  }

  @NotNull
  @Override
  public final SerializedStubTree read(@NotNull final DataInput in) throws IOException {
    if (PersistentHashMapValueStorage.COMPRESSION_ENABLED) {
      int serializedStubsLength = DataInputOutputUtil.readINT(in);
      byte[] bytes = new byte[serializedStubsLength];
      in.readFully(bytes);
      int indexedStubByteLength;
      byte[] indexedStubBytes;
      indexedStubByteLength = DataInputOutputUtil.readINT(in);
      indexedStubBytes = new byte[indexedStubByteLength];
      in.readFully(indexedStubBytes);
      IndexingStampInfo indexingStampInfo = mySaveIndexingStamp ? IndexingStampInfo.read((DataInputStream)in) : null;
      return new SerializedStubTree(bytes, bytes.length, indexedStubBytes, indexedStubByteLength,
                                    null, myStubIndexesExternalizer, mySerializationManager,  indexingStampInfo);
    }
    else {
      byte[] treeBytes = CompressionUtil.readCompressed(in);
      byte[] indexedStubBytes = CompressionUtil.readCompressed(in);
      IndexingStampInfo indexingStampInfo = mySaveIndexingStamp ? IndexingStampInfo.read((DataInputStream)in) : null;
      return new SerializedStubTree(treeBytes, treeBytes.length, indexedStubBytes, indexedStubBytes.length,
                                    null, myStubIndexesExternalizer, mySerializationManager, indexingStampInfo);
    }
  }
}
