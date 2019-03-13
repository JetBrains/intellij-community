// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

public class MaybeRelativeFileKeyDescriptor extends FileKeyDescriptor {
  private final MaybeRelativizer myRelativizer;

  public MaybeRelativeFileKeyDescriptor(MaybeRelativizer relativizer) {
    myRelativizer = relativizer;
  }

  @Override
  public void save(@NotNull DataOutput out, File value) throws IOException {
    IOUtil.writeUTF(out, myRelativizer.toRelative(value.getPath()));
  }

  @Override
  public File read(@NotNull DataInput in) throws IOException {
    return new File(myRelativizer.toFull(IOUtil.readUTF(in)));
  }
}
