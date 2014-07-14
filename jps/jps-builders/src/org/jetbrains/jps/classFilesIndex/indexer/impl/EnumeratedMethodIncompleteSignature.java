/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public class EnumeratedMethodIncompleteSignature {

  private final int myOwner;
  private final int myName;
  private final boolean myStatic;

  public EnumeratedMethodIncompleteSignature(final int owner, final int name, final boolean aStatic) {
    myOwner = owner;
    myName = name;
    myStatic = aStatic;
  }

  public int getOwner() {
    return myOwner;
  }

  public int getName() {
    return myName;
  }

  public boolean isStatic() {
    return myStatic;
  }

  public static DataExternalizer<EnumeratedMethodIncompleteSignature> createDataExternalizer() {
    return new DataExternalizer<EnumeratedMethodIncompleteSignature>() {
      @Override
      public void save(@NotNull final DataOutput out, final EnumeratedMethodIncompleteSignature value) throws IOException {
        out.writeInt(value.getOwner());
        out.writeInt(value.getName());
        out.writeBoolean(value.isStatic());
      }

      @Override
      public EnumeratedMethodIncompleteSignature read(@NotNull final DataInput in) throws IOException {
        return new EnumeratedMethodIncompleteSignature(in.readInt(),
                                                       in.readInt(),
                                                       in.readBoolean());
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EnumeratedMethodIncompleteSignature that = (EnumeratedMethodIncompleteSignature)o;

    if (myName != that.myName) return false;
    if (myOwner != that.myOwner) return false;
    if (myStatic != that.myStatic) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myOwner;
    result = 31 * result + myName;
    result = 31 * result + (myStatic ? 1 : 0);
    return result;
  }
}
