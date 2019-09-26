// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

public class PortableFileKeyDescriptor implements KeyDescriptor<File> {
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
    // On case insensitive OS hash calculated from path converted to lower case
    if (value == null) return 0;
    String path = value.getPath();
    return StringUtil.isEmpty(path) ? 0 : FileUtil.toCanonicalPath(path).hashCode();
  }

  @Override
  public boolean isEqual(File val1, File val2) {
    // On case insensitive OS hash calculated from path converted to lower case
    if (val1 == val2) return true;
    if (val1 == null || val2 == null) return false;

    String path1 = FileUtil.toCanonicalPath(val1.getPath());
    String path2 = FileUtil.toCanonicalPath(val2.getPath());
    return path1.equals(path2);
  }
}
