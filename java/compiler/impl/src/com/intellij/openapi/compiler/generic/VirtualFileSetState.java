/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.generic;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class VirtualFileSetState {
  public static final DataExternalizer<VirtualFileSetState> EXTERNALIZER = new VirtualFileWithDependenciesExternalizer();
  private Map<String, Long> myTimestamps = new HashMap<>();

  public VirtualFileSetState() {
  }

  public VirtualFileSetState(Collection<? extends VirtualFile> files) {
    for (VirtualFile file : files) {
      addFile(file);
    }
  }

  public void addFile(@NotNull VirtualFile file) {
    myTimestamps.put(file.getUrl(), file.getTimeStamp());
  }

  public boolean isUpToDate(Set<? extends VirtualFile> files) {
    if (files.size() != myTimestamps.size()) {
      return false;
    }

    for (VirtualFile file : files) {
      final Long timestamp = myTimestamps.get(file.getUrl());
      if (timestamp == null || timestamp != file.getTimeStamp()) {
        return false;
      }
    }
    return true;
  }


  private static class VirtualFileWithDependenciesExternalizer implements DataExternalizer<VirtualFileSetState> {
    @Override
    public void save(@NotNull DataOutput out, VirtualFileSetState value) throws IOException {
      final Map<String, Long> dependencies = value.myTimestamps;
      out.writeInt(dependencies.size());
      for (Map.Entry<String, Long> entry : dependencies.entrySet()) {
        IOUtil.writeUTF(out, entry.getKey());
        out.writeLong(entry.getValue());
      }
    }

    @Override
    public VirtualFileSetState read(@NotNull DataInput in) throws IOException {
      final VirtualFileSetState state = new VirtualFileSetState();
      int size = in.readInt();
      while (size-- > 0) {
        final String url = IOUtil.readUTF(in);
        final long timestamp = in.readLong();
        state.myTimestamps.put(url, timestamp);
      }
      return state;
    }
  }
}
