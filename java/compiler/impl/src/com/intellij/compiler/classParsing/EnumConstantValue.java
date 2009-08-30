/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class EnumConstantValue extends ConstantValue{
  private final int myTypeName;
  private final int myConstantName;

  public EnumConstantValue(int typeName, int constantName) {
    myTypeName = typeName;
    myConstantName = constantName;
  }

  public EnumConstantValue(DataInput in) throws IOException{
    myTypeName = in.readInt();
    myConstantName = in.readInt();
  }

  public int getTypeName() {
    return myTypeName;
  }

  public int getConstantName() {
    return myConstantName;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeInt(myTypeName);
    out.writeInt(myConstantName);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EnumConstantValue)) return false;

    final EnumConstantValue enumConstantValue = (EnumConstantValue)o;

    if (myConstantName != enumConstantValue.myConstantName) return false;
    if (myTypeName != enumConstantValue.myTypeName) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myTypeName;
    result = 29 * result + myConstantName;
    return result;
  }
}
