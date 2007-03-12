/*
 * @author Eugene Zhuravlev
 */
package com.intellij.compiler.classParsing;

import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.IOException;

public class ReferenceInfo extends ItemInfo {

  public ReferenceInfo(int declaringClassName) {
    super(declaringClassName);
  }

  public ReferenceInfo(DataInput in) throws IOException {
    super(in);
  }

  public @NonNls String toString() { // for debug purposes
    return "Class reference[class name=" + String.valueOf(getClassName()) + "]";
  }
}
