// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
* @author Eugene Zhuravlev
*/
public class FileKeyDescriptor implements KeyDescriptor<File> {
  @Override
  public void save(@NotNull DataOutput out, File value) throws IOException {
    IOUtil.writeUTF(out, value.getPath());
  }

  @Override
  public File read(@NotNull DataInput in) throws IOException {
    return new File(IOUtil.readUTF(in));
  }

  @Override
  public int getHashCode(File value) {
    return FileUtil.fileHashCode(value);
  }

  @Override
  public boolean isEqual(File val1, File val2) {
    return FileUtil.filesEqual(val1, val2);
  }
}
