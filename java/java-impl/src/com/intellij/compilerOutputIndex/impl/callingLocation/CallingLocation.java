package com.intellij.compilerOutputIndex.impl.callingLocation;

import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class CallingLocation {
  @NotNull
  private final MethodIncompleteSignature myMethodIncompleteSignature;
  @NotNull
  private final VariableType myVariableType;

  public CallingLocation(@NotNull final MethodIncompleteSignature methodIncompleteSignature, @NotNull final VariableType variableType) {
    myMethodIncompleteSignature = methodIncompleteSignature;
    myVariableType = variableType;
  }

  @NotNull
  public MethodIncompleteSignature getMethodIncompleteSignature() {
    return myMethodIncompleteSignature;
  }

  @NotNull
  public VariableType getVariableType() {
    return myVariableType;
  }

  public static DataExternalizer<CallingLocation> createDataExternalizer() {
    final KeyDescriptor<MethodIncompleteSignature> methodIncompleteSignatureKeyDescriptor = MethodIncompleteSignature.createKeyDescriptor();
    return new DataExternalizer<CallingLocation>() {
      @Override
      public void save(final DataOutput out, final CallingLocation value) throws IOException {
        methodIncompleteSignatureKeyDescriptor.save(out, value.getMethodIncompleteSignature());
        VariableType.KEY_DESCRIPTOR.save(out, value.getVariableType());
      }

      @Override
      public CallingLocation read(final DataInput in) throws IOException {
        return new CallingLocation(methodIncompleteSignatureKeyDescriptor.read(in), VariableType.KEY_DESCRIPTOR.read(in));
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final CallingLocation that = (CallingLocation)o;

    if (!myMethodIncompleteSignature.equals(that.myMethodIncompleteSignature)) return false;
    if (myVariableType != that.myVariableType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMethodIncompleteSignature.hashCode();
    result = 31 * result + myVariableType.hashCode();
    return result;
  }
}
