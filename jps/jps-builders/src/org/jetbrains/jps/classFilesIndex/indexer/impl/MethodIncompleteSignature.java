/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.classFilesIndex.indexer.impl;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.classFilesIndex.AsmUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;

/**
 * @author Dmitry Batkovich
 */
public class MethodIncompleteSignature {
  public static final String CONSTRUCTOR_METHOD_NAME = "<init>";

  @NotNull
  private final String myOwner;
  @NotNull
  private final String myReturnType;
  @NotNull
  private final String myName;
  private final boolean myStatic;

  public MethodIncompleteSignature(@NotNull final String owner,
                                   @NotNull final String returnType,
                                   @NotNull final String name,
                                   final boolean aStatic) {
    myOwner = owner;
    myReturnType = returnType;
    myName = name;
    myStatic = aStatic;
  }

  public static MethodIncompleteSignature constructor(@NotNull final String className) {
    return new MethodIncompleteSignature(className, className, CONSTRUCTOR_METHOD_NAME, true);
  }

  public MethodIncompleteSignature toExternalRepresentation() {
    return new MethodIncompleteSignature(AsmUtil.getQualifiedClassName(getOwner()),
                                         AsmUtil.getQualifiedClassName(getReturnType()),
                                         getName(),
                                         isStatic());
  }

  @NotNull
  public String getOwner() {
    return myOwner;
  }

  @NotNull
  public String getReturnType() {
    return myReturnType;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isStatic() {
    return myStatic;
  }

  @Override
  public String toString() {
    return "MethodIncompleteSignature{" +
           "myOwner='" + myOwner + '\'' +
           ", myReturnType='" + myReturnType + '\'' +
           ", myName='" + myName + '\'' +
           ", myStatic=" + myStatic +
           '}';
  }

  public final static Comparator<MethodIncompleteSignature> COMPARATOR = new Comparator<MethodIncompleteSignature>() {
    @Override
    public int compare(final MethodIncompleteSignature o1, final MethodIncompleteSignature o2) {
      int sub = o1.getOwner().compareTo(o2.getOwner());
      if (sub != 0) {
        return sub;
      }
      sub = o1.getName().compareTo(o2.getName());
      if (sub != 0) {
        return sub;
      }
      sub = o1.getReturnType().compareTo(o2.getReturnType());
      if (sub != 0) {
        return sub;
      }
      if (o1.isStatic() && !o2.isStatic()) {
        return 1;
      }
      if (o2.isStatic() && !o1.isStatic()) {
        return -1;
      }
      return 0;
    }
  };

  public static DataExternalizer<MethodIncompleteSignature> createDataExternalizer() {
    final EnumeratorStringDescriptor stringDescriptor = new EnumeratorStringDescriptor();
    return new DataExternalizer<MethodIncompleteSignature>() {
      @Override
      public void save(final DataOutput out, final MethodIncompleteSignature value) throws IOException {
        stringDescriptor.save(out, value.getOwner());
        stringDescriptor.save(out, value.getReturnType());
        stringDescriptor.save(out, value.getName());
        out.writeBoolean(value.isStatic());
      }

      @Override
      public MethodIncompleteSignature read(final DataInput in) throws IOException {
        return new MethodIncompleteSignature(stringDescriptor.read(in), stringDescriptor.read(in), stringDescriptor.read(in),
                                             in.readBoolean());
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodIncompleteSignature that = (MethodIncompleteSignature)o;

    if (myStatic != that.myStatic) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myOwner.equals(that.myOwner)) return false;
    if (!myReturnType.equals(that.myReturnType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myOwner.hashCode();
    result = 31 * result + myReturnType.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + (myStatic ? 1 : 0);
    return result;
  }
}
