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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class VirtualFileCompileItem<State extends VirtualFilePersistentState> extends CompileItem<String, State> {
  public static final KeyDescriptor<String> KEY_DESCRIPTOR = new EnumeratorStringDescriptor();
  protected final VirtualFile myFile;

  public VirtualFileCompileItem(@NotNull VirtualFile file) {
    myFile = file;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  public final boolean isUpToDate(@NotNull State state) {
    if (myFile.getTimeStamp() != state.getSourceTimestamp()) {
      return false;
    }
    return isStateUpToDate(state);
  }

  protected abstract boolean isStateUpToDate(State state);

  @NotNull
  @Override
  public String getKey() {
    return myFile.getUrl();
  }

}
