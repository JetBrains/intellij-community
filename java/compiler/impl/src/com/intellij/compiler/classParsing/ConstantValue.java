/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataOutput;
import java.io.IOException;

public class ConstantValue {
  public static final ConstantValue EMPTY_CONSTANT_VALUE = new ConstantValue();

  protected ConstantValue() {
  }

  public void save(DataOutput out) throws IOException {
  }

}
