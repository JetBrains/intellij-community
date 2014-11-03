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
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.refResolve.RefResolveServiceImpl;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Collections;
import java.util.Map;

// all it does is take files it was fed and queue them to the resolve
public class RefQueueIndex extends FileBasedIndexExtension<Void,Void> {
  private static final ID<Void, Void> ID = com.intellij.util.indexing.ID.create("RefQueueIndex");

  @NotNull
  @Override
  public ID<Void, Void> getName() {
    return ID;
  }

  @NotNull
  @Override
  public DataIndexer<Void, Void, FileContent> getIndexer() {
    return new DataIndexer<Void, Void, FileContent>() {
      @NotNull
      @Override
      public Map<Void, Void> map(@NotNull FileContent inputData) {
        if (RefResolveService.ENABLED) {
          Project project = inputData.getProject();
          RefResolveService.getInstance(project).queue(Collections.singletonList(inputData.getFile()), "Cache updater");
        }
        return Collections.emptyMap();
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<Void> getKeyDescriptor() {
    return new KeyDescriptor<Void>() {
      @Override
      public void save(@NotNull DataOutput out, Void value) {

      }

      @Override
      public Void read(@NotNull DataInput in) {
        return null;
      }

      @Override
      public int getHashCode(Void value) {
        return 0;
      }

      @Override
      public boolean isEqual(Void val1, Void val2) {
        return false;
      }
    };
  }

  @NotNull
  @Override
  public DataExternalizer<Void> getValueExternalizer() {
    return new DataExternalizer<Void>() {
      @Override
      public void save(@NotNull DataOutput out, Void value) {

      }

      @Override
      public Void read(@NotNull DataInput in) {
        return null;
      }
    };
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileBasedIndex.InputFilter() {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return RefResolveService.ENABLED && !file.isDirectory() && RefResolveServiceImpl.isSupportedFileType(file);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return RefResolveService.ENABLED ? 0xFF : 0;
  }
}
