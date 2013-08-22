package com.intellij.compilerOutputIndex.impl.callingLocation;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public class MethodNameAndQualifier {
  @NotNull
  private final String myMethodName;
  @NotNull
  private final String myQualifierName;

  public MethodNameAndQualifier(@NotNull final String methodName, @NotNull final String qualifierName) {
    myMethodName = methodName;
    myQualifierName = qualifierName;
  }

  @NotNull
  public String getMethodName() {
    return myMethodName;
  }

  @NotNull
  public String getQualifierName() {
    return myQualifierName;
  }

  public static KeyDescriptor<MethodNameAndQualifier> createKeyDescriptor() {
    final DataExternalizer<String> stringDataExternalizer = new EnumeratorStringDescriptor();
    return new KeyDescriptor<MethodNameAndQualifier>() {
      @Override
      public void save(final DataOutput out, final MethodNameAndQualifier value) throws IOException {
        stringDataExternalizer.save(out, value.myMethodName);
        stringDataExternalizer.save(out, value.myQualifierName);
      }

      @Override
      public MethodNameAndQualifier read(final DataInput in) throws IOException {
        return new MethodNameAndQualifier(stringDataExternalizer.read(in), stringDataExternalizer.read(in));
      }

      @Override
      public int getHashCode(final MethodNameAndQualifier value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(final MethodNameAndQualifier val1, final MethodNameAndQualifier val2) {
        return val1.equals(val2);
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodNameAndQualifier that = (MethodNameAndQualifier)o;

    if (!myMethodName.equals(that.myMethodName)) return false;
    if (!myQualifierName.equals(that.myQualifierName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMethodName.hashCode();
    result = 31 * result + myQualifierName.hashCode();
    return result;
  }
}
