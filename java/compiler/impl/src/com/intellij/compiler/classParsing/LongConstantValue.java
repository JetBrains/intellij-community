/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LongConstantValue extends ConstantValue{
  private final long myValue;

  public LongConstantValue(long value) {
    myValue = value;
  }
  public LongConstantValue(DataInput in) throws IOException{
    myValue = in.readLong();
  }

  public long getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeLong(myValue);
  }

  public boolean equals(Object obj) {
    return (obj instanceof LongConstantValue) && (((LongConstantValue)obj).myValue == myValue);
  }
}
