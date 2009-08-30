/*
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 1:53:00 PM
 */
package com.intellij.compiler.impl;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

public class TimestampCache extends StateCache <Long> {
  public TimestampCache(File storeDirectory) throws IOException {
    super(new File(storeDirectory, "timestamps"));
  }

  public void update(String url, @NotNull Long state) throws IOException {
    super.update(url, state);
  }

  public Long read(DataInput stream) throws IOException {
    return stream.readLong();
  }

  public void write(Long aLong, DataOutput out) throws IOException {
    out.writeLong(aLong.longValue());
  }
}
