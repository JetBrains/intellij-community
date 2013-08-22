package com.intellij.compilerOutputIndex.impl.singleton;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public class MethodShortSignature {
  @NotNull
  private final String myName;
  @NotNull
  private final String mySignature; //in raw asm type

  public MethodShortSignature(final @NotNull String name, final @NotNull String signature) {
    myName = name;
    mySignature = signature;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getSignature() {
    return mySignature;
  }

  public static DataExternalizer<MethodShortSignature> createDataExternalizer() {
    final EnumeratorStringDescriptor stringDescriptor = new EnumeratorStringDescriptor();
    return new DataExternalizer<MethodShortSignature>() {

      @Override
      public void save(final DataOutput out, final MethodShortSignature value) throws IOException {
        stringDescriptor.save(out, value.getName());
        stringDescriptor.save(out, value.getSignature());
      }

      @Override
      public MethodShortSignature read(final DataInput in) throws IOException {
        return new MethodShortSignature(stringDescriptor.read(in), stringDescriptor.read(in));
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodShortSignature that = (MethodShortSignature) o;

    if (!myName.equals(that.myName)) return false;
    if (!mySignature.equals(that.mySignature)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + mySignature.hashCode();
    return result;
  }
}
