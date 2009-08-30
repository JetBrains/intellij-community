/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FloatConstantValue extends ConstantValue{
  private final float myValue;

  public FloatConstantValue(float value) {
    myValue = value;
  }

  public FloatConstantValue(DataInput in) throws IOException{
    myValue = in.readFloat();
  }

  public float getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeFloat(myValue);
  }

  public boolean equals(Object obj) {
    return (obj instanceof FloatConstantValue) && (((FloatConstantValue)obj).myValue == myValue);
  }
}
