package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author max
 */
public class IntValueProvider implements ByteBufferMap.ValueProvider<Integer> {
  public static IntValueProvider INSTANCE = new IntValueProvider();

  private IntValueProvider() {
  }

  public void write(DataOutput out, Integer value) throws IOException {
    out.writeInt(((Integer)value).intValue());
  }

  public int length(Integer value) {
    return 4;
  }

  public Integer get(DataInput in) throws IOException {
    return new Integer(in.readInt());
  }
}
