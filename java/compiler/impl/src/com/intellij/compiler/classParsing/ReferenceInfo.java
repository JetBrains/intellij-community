/*
 * @author Eugene Zhuravlev
 */
package com.intellij.compiler.classParsing;

import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ReferenceInfo {
  private final int myClassName;

  public ReferenceInfo(int declaringClassName) {
    myClassName = declaringClassName;
  }

  public ReferenceInfo(DataInput in) throws IOException {
    this(in.readInt());
  }

  public @NonNls String toString() { // for debug purposes
    return "Class reference[class name=" + String.valueOf(getClassName()) + "]";
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myClassName);
  }

  public int getClassName() {
    return myClassName;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ReferenceInfo that = (ReferenceInfo)o;

    if (myClassName != that.myClassName) return false;

    return true;
  }

  public int hashCode() {
    return myClassName;
  }
}
