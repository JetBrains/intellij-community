/*
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 1:53:00 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

public class TimestampCache extends StateCache <Long> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.TsCache");
  public TimestampCache(File storeDirectory) throws IOException {
    super(new File(storeDirectory, "timestamps"));
  }

  public void update(String url, Long state) throws IOException {
    LOG.assertTrue(state != null);
    super.update(url, state);
  }

  public Long read(DataInput stream) throws IOException {
    return stream.readLong();
  }

  public void write(Long aLong, DataOutput out) throws IOException {
    out.writeLong(aLong.longValue());
  }
}
