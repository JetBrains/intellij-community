/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class DoubleConstantValue extends ConstantValue{
  private final double myValue;

  public DoubleConstantValue(double value) {
    myValue = value;
  }

  public DoubleConstantValue(DataInput in) throws IOException{
    myValue = in.readDouble();
  }

  public double getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeDouble(myValue);
  }

  public boolean equals(Object obj) {
    return (obj instanceof DoubleConstantValue) && (((DoubleConstantValue)obj).myValue == myValue);
  }
}
