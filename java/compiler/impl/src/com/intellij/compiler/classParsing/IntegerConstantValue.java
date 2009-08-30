/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class IntegerConstantValue extends ConstantValue{
  private final int myValue;

  public IntegerConstantValue(int value) {
    myValue = value;
  }

  public IntegerConstantValue(DataInput in) throws IOException{
    myValue = in.readInt();
  }

  public int getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeInt(myValue);
  }

  public boolean equals(Object obj) {
    return (obj instanceof IntegerConstantValue) && (((IntegerConstantValue)obj).myValue == myValue);
  }
}
