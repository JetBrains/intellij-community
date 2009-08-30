/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class StringConstantValue extends ConstantValue{
  private final String myValue;

  public StringConstantValue(String value) {
    myValue = value;
  }

  public StringConstantValue(DataInput in) throws IOException{
    myValue = in.readUTF();
  }

  public String getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeUTF(myValue);
  }

  public boolean equals(Object obj) {
    return
      (obj instanceof StringConstantValue) &&
      ((myValue == null)? ((StringConstantValue)obj).myValue == null : myValue.equals(((StringConstantValue)obj).myValue));
  }
}
