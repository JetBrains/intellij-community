/**
 * created at Jan 10, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FieldInfo extends MemberInfo {
  public static final FieldInfo[] EMPTY_ARRAY = new FieldInfo[0];
  private final ConstantValue myConstantValue;

  public FieldInfo(int name, int descriptor) {
    super(name, descriptor);
    myConstantValue = ConstantValue.EMPTY_CONSTANT_VALUE;
  }

  public FieldInfo(int name, int descriptor, final int genericSignature, int flags, ConstantValue value, final AnnotationConstantValue[] runtimeVisibleAnnotations, final AnnotationConstantValue[] runtimeInvisibleAnnotations) {
    super(name, descriptor, genericSignature, flags, runtimeVisibleAnnotations, runtimeInvisibleAnnotations);
    myConstantValue = value == null ? ConstantValue.EMPTY_CONSTANT_VALUE : value;
  }

  public FieldInfo(DataInput in) throws IOException {
    super(in);
    myConstantValue = MemberInfoExternalizer.loadConstantValue(in);
  }

  public ConstantValue getConstantValue() {
    return myConstantValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    MemberInfoExternalizer.saveConstantValue(out, myConstantValue);
  }

}
