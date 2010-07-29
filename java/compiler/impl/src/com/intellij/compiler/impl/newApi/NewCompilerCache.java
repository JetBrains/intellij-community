/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.compiler.impl.newApi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Processor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class NewCompilerCache<Key, State> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.newApi.NewCompilerCache");
  private PersistentHashMap<KeyAndTargetData<Key>, State> myPersistentMap;
  private File myCacheFile;
  private final NewCompiler<Key, State> myCompiler;

  public NewCompilerCache(NewCompiler<Key, State> compiler, final File compilerCacheDir) throws IOException {
    myCompiler = compiler;
    myCacheFile = new File(compilerCacheDir, "timestamps");
    createMap();
  }

  private void createMap() throws IOException {
    myPersistentMap = new PersistentHashMap<KeyAndTargetData<Key>, State>(myCacheFile, new SourceItemDataDescriptor(myCompiler.getItemKeyDescriptor()),
                                                                  myCompiler.getItemStateExternalizer());
  }

  private KeyAndTargetData<Key> getKeyAndTargetData(Key key, int target) {
    KeyAndTargetData<Key> data = new KeyAndTargetData<Key>();
    data.myTarget = target;
    data.myKey = key;
    return data;
  }

  public void wipe() throws IOException {
    try {
      myPersistentMap.close();
    }
    catch (IOException ignored) {
    }
    PersistentHashMap.deleteFilesStartingWith(myCacheFile);
    createMap();
  }

  public void close() {
    try {
      myPersistentMap.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public void remove(int targetId, Key key) throws IOException {
    myPersistentMap.remove(getKeyAndTargetData(key, targetId));
  }

  public State getState(int targetId, Key key) throws IOException {
    return myPersistentMap.get(getKeyAndTargetData(key, targetId));
  }

  public void processSources(final int targetId, final Processor<Key> processor) throws IOException {
    myPersistentMap.processKeys(new Processor<KeyAndTargetData<Key>>() {
      @Override
      public boolean process(KeyAndTargetData<Key> data) {
        return targetId == data.myTarget ? processor.process(data.myKey) : true;
      }
    });
  }

  public void putOutput(int targetId, Key key, State outputItem) throws IOException {
    myPersistentMap.put(getKeyAndTargetData(key, targetId), outputItem);
  }


  private static class KeyAndTargetData<Key> {
    public int myTarget;
    public Key myKey;
  }

  private class SourceItemDataDescriptor implements KeyDescriptor<KeyAndTargetData<Key>> {
    private final KeyDescriptor<Key> myKeyDescriptor;

    public SourceItemDataDescriptor(KeyDescriptor<Key> keyDescriptor) {
      myKeyDescriptor = keyDescriptor;
    }

    @Override
    public boolean isEqual(KeyAndTargetData<Key> val1, KeyAndTargetData<Key> val2) {
      return val1.myTarget == val2.myTarget;
    }

    @Override
    public int getHashCode(KeyAndTargetData<Key> value) {
      return value.myTarget + 239 * myKeyDescriptor.getHashCode(value.myKey);
    }

    @Override
    public void save(DataOutput out, KeyAndTargetData<Key> value) throws IOException {
      out.writeInt(value.myTarget);
      myKeyDescriptor.save(out, value.myKey);
    }


    @Override
    public KeyAndTargetData<Key> read(DataInput in) throws IOException {
      int target = in.readInt();
      final Key item = myKeyDescriptor.read(in);
      return getKeyAndTargetData(item, target);
    }
  }
}
