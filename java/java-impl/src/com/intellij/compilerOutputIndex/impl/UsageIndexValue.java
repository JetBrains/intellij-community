package com.intellij.compilerOutputIndex.impl;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public class UsageIndexValue implements Comparable<UsageIndexValue> {
  private final int myOccurrences;
  private final MethodIncompleteSignature myMethodIncompleteSignature;

  public UsageIndexValue(final MethodIncompleteSignature signature, final int occurrences) {
    myOccurrences = occurrences;
    myMethodIncompleteSignature = signature;
  }

  public int getOccurrences() {
    return myOccurrences;
  }

  public MethodIncompleteSignature getMethodIncompleteSignature() {
    return myMethodIncompleteSignature;
  }

  public static DataExternalizer<UsageIndexValue> createDataExternalizer() {
    final DataExternalizer<MethodIncompleteSignature> methodInvocationDataExternalizer = MethodIncompleteSignature.createKeyDescriptor();
    return new DataExternalizer<UsageIndexValue>() {
      @Override
      public void save(final DataOutput out, final UsageIndexValue value) throws IOException {
        methodInvocationDataExternalizer.save(out, value.myMethodIncompleteSignature);
        out.writeInt(value.myOccurrences);
      }

      @Override
      public UsageIndexValue read(final DataInput in) throws IOException {
        return new UsageIndexValue(methodInvocationDataExternalizer.read(in), in.readInt());
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final UsageIndexValue that = (UsageIndexValue)o;

    return myOccurrences == that.myOccurrences && myMethodIncompleteSignature.equals(that.myMethodIncompleteSignature);
  }

  @Override
  public int hashCode() {
    int result = myOccurrences;
    result = 31 * result + myMethodIncompleteSignature.hashCode();
    return result;
  }

  @Override
  public int compareTo(@NotNull final UsageIndexValue that) {
    final int sub = -myOccurrences + that.myOccurrences;
    if (sub != 0) return sub;
    return MethodIncompleteSignature.COMPARATOR.compare(myMethodIncompleteSignature, that.myMethodIncompleteSignature);
  }
}
