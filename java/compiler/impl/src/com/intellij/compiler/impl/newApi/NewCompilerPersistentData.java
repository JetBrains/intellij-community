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
import com.intellij.util.io.IOUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class NewCompilerPersistentData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.newApi.NewCompilerPersistentData");
  private static final int VERSION = 0;
  private File myFile;
  private Map<String, Integer> myTarget2Id = new HashMap<String, Integer>();
  private TIntHashSet myUsedIds = new TIntHashSet();
  private boolean myVersionChanged;
  private final int myCompilerVersion;

  public NewCompilerPersistentData(File cacheStoreDirectory, int compilerVersion) throws IOException {
    myCompilerVersion = compilerVersion;
    myFile = new File(cacheStoreDirectory, "info");
    if (!myFile.exists()) {
      LOG.info("Compiler info file doesn't exists: " + myFile.getAbsolutePath());
      myVersionChanged = true;
      return;
    }

    DataInputStream input = new DataInputStream(new FileInputStream(myFile));
    try {
      final int dataVersion = input.readInt();
      if (dataVersion != VERSION) {
        LOG.info("Version of compiler info file (" + myFile.getAbsolutePath() + ") changed: " + dataVersion + " -> " + VERSION);
        myVersionChanged = true;
        return;
      }

      final int savedCompilerVersion = input.readInt();
      if (savedCompilerVersion != compilerVersion) {
        LOG.info("Compiler caches version changed (" + myFile.getAbsolutePath() + "): " + savedCompilerVersion + " -> " + compilerVersion);
        myVersionChanged = true;
        return;
      }

      int size = input.readInt();
      while (size-- > 0) {
        final String target = IOUtil.readString(input);
        final int id = input.readInt();
        myTarget2Id.put(target, id);
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

  public void save() throws IOException {
    final DataOutputStream output = new DataOutputStream(new FileOutputStream(myFile));
    try {
      output.writeInt(VERSION);
      output.writeInt(myCompilerVersion);
      output.writeInt(myTarget2Id.size());

      for (Map.Entry<String, Integer> entry : myTarget2Id.entrySet()) {
        IOUtil.writeString(entry.getKey(), output);
        output.writeInt(entry.getValue());
      }
    }
    finally {
      output.close();
    }
  }

  public int getId(@NotNull String target) {
    if (myTarget2Id.containsKey(target)) {
      return myTarget2Id.get(target);
    }
    int id = 0;
    while (myUsedIds.contains(id)) {
      id++;
    }
    myTarget2Id.put(target, id);
    myUsedIds.add(id);
    return id;
  }

  public Set<String> getAllTargets() {
    return myTarget2Id.keySet();
  }

  public int removeId(String target) {
    return myTarget2Id.remove(target);
  }
}
