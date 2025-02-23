// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.Difference;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Objects;

public abstract class AnnotationInstance implements ExternalizableGraphElement {
  
  private final TypeRepr.ClassType annotationClass;
  private final Object contentHash;

  protected AnnotationInstance(@NotNull TypeRepr.ClassType annotClass, Object contentHash) {
    this.annotationClass = annotClass;
    this.contentHash = contentHash;
  }

  protected AnnotationInstance(GraphDataInput in) throws IOException {
    annotationClass = new TypeRepr.ClassType(in.readUTF());
    contentHash = JvmProtoMemberValueExternalizer.read(in);
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    out.writeUTF(annotationClass.getJvmName());
    JvmProtoMemberValueExternalizer.write(out, contentHash);
  }

  public @NotNull TypeRepr.ClassType getAnnotationClass() {
    return annotationClass;
  }

  public Object getContentHash() {
    return contentHash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final AnnotationInstance that = (AnnotationInstance)o;
    return annotationClass.equals(that.annotationClass) && Objects.deepEquals(contentHash, that.contentHash);
  }

  @Override
  public int hashCode() {
    int result = annotationClass.hashCode();
    result = 31 * result + getContentHashCode(contentHash);
    return result;
  }

  private static int getContentHashCode(Object data) {
    if (data != null && data.getClass().isArray()) {
      int result = 1;
      for (int idx = 0, length = Array.getLength(data); idx < length; idx++) {
        result = 31 * result + getContentHashCode(Array.get(data, idx));
      }
      return result;
    }
    return Objects.hashCode(data);
  }

  public abstract class Diff<V extends AnnotationInstance> implements Difference {

    private final V myPast;

    public Diff(V past) {
      myPast = past;
    }

    @Override
    public boolean unchanged() {
      return !contentHashChanged();
    }

    public boolean contentHashChanged() {
      return !Objects.deepEquals(myPast.getContentHash(), contentHash);
    }
  }
}
