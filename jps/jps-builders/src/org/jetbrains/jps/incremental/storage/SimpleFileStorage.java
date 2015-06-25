/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.StorageProvider;

import java.io.*;

/**
 * Implementation of {@link StorageOwner} which stores data in a single file.
 *
 * @see BuildDataManager#getStorage(BuildTarget, StorageProvider)
 * @author nik
 */
public class SimpleFileStorage<T> implements StorageOwner {
  private T myState;
  private final File myFile;
  private final DataExternalizer<T> myExternalizer;

  public SimpleFileStorage(File file, DataExternalizer<T> externalizer) throws IOException {
    myFile = file;
    myExternalizer = externalizer;
    try {
      DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(myFile)));
      try {
        myState = externalizer.read(input);
      }
      finally {
        input.close();
      }
    }
    catch (IOException e) {
      myState = null;
    }
  }

  @Nullable
  public T getState() {
    return myState;
  }

  public void setState(@NotNull T state) throws IOException {
    myState = state;
    DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(myFile)));
    try {
      myExternalizer.save(output, state);
    }
    finally {
      output.close();
    }
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
  }

  @Override
  public void clean() throws IOException {
    FileUtil.delete(myFile);
  }

  @Override
  public void close() throws IOException {
  }

  public static class Provider<T> extends StorageProvider<SimpleFileStorage<T>> {
    private String myFileName;
    private DataExternalizer<T> myExternalizer;

    public Provider(@NotNull String fileName, @NotNull DataExternalizer<T> externalizer) {
      myFileName = fileName;
      myExternalizer = externalizer;
    }

    @NotNull
    @Override
    public SimpleFileStorage<T> createStorage(File targetDataDir) throws IOException {
      return new SimpleFileStorage<T>(new File(targetDataDir, myFileName), myExternalizer);
    }
  }
}
