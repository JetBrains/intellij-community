package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ClassInfoConstantValue extends ConstantValue{
  private final int myValue;

  public ClassInfoConstantValue(int value) {
    myValue = value;
  }

  public ClassInfoConstantValue(DataInput in) throws IOException{
    myValue = in.readInt();
  }

  public int getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeInt(myValue);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassInfoConstantValue)) return false;

    final ClassInfoConstantValue classInfoConstantValue = (ClassInfoConstantValue)o;

    if (myValue != classInfoConstantValue.myValue) return false;

    return true;
  }

  public int hashCode() {
    return myValue;
  }
}
