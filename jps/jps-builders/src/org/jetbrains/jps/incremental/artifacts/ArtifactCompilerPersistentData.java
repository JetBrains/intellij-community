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
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IOUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class ArtifactCompilerPersistentData implements StorageOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.generic.ArtifactCompilerPersistentData");
  private static final int VERSION = 1;
  private File myFile;
  private Map<String, Integer> myArtifact2Id = new HashMap<String, Integer>();
  private TIntHashSet myUsedIds = new TIntHashSet();
  private boolean myVersionChanged;

  public ArtifactCompilerPersistentData(File cacheStoreDirectory) throws IOException {
    myFile = new File(cacheStoreDirectory, "info");
    if (!myFile.exists()) {
      LOG.debug("Artifacts compiler info file doesn't exist: " + myFile.getAbsolutePath());
      myVersionChanged = true;
      return;
    }

    DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(myFile)));
    try {
      final int version = input.readInt();
      if (version != VERSION) {
        LOG.debug("Artifacts compiler version changed (" + myFile.getAbsolutePath() + "): " + version + " -> " + VERSION);
        myVersionChanged = true;
        return;
      }

      int size = input.readInt();
      while (size-- > 0) {
        final String artifactName = IOUtil.readString(input);
        final int id = input.readInt();
        myArtifact2Id.put(artifactName, id);
        myUsedIds.add(id);
      }
    }
    finally {
      input.close();
    }
  }

  public boolean isVersionChanged() {
    return myVersionChanged;
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
    try {
      save();
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @Override
  public void close() throws IOException {
    save();
  }

  private void save() throws IOException {
    final DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(myFile)));
    try {
      output.writeInt(VERSION);
      output.writeInt(myArtifact2Id.size());

      for (Map.Entry<String, Integer> entry : myArtifact2Id.entrySet()) {
        IOUtil.writeString(entry.getKey(), output);
        output.writeInt(entry.getValue());
      }
    }
    finally {
      output.close();
    }
  }

  public int getId(@NotNull String artifactName) {
    if (myArtifact2Id.containsKey(artifactName)) {
      return myArtifact2Id.get(artifactName);
    }
    int id = 0;
    while (myUsedIds.contains(id)) {
      id++;
    }
    myArtifact2Id.put(artifactName, id);
    myUsedIds.add(id);
    return id;
  }

  public Set<String> getAllArtifacts() {
    return myArtifact2Id.keySet();
  }

  public int removeArtifact(String target) {
    return myArtifact2Id.remove(target);
  }

  public void clean() {
    FileUtil.delete(myFile);
  }
}
