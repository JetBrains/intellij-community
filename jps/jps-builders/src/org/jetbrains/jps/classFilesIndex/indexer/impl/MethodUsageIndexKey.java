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
import com.intellij.util.io.EnumDataDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public class MethodUsageIndexKey {
  @NotNull
  private final String myQualifiedClassName;
  @NotNull
  private final KeyRole myRole;

  public MethodUsageIndexKey(@NotNull final String qualifiedClassName, @NotNull final KeyRole role) {
    myQualifiedClassName = qualifiedClassName;
    myRole = role;
  }

  @NotNull
  public String getQualifiedClassName() {
    return myQualifiedClassName;
  }

  @NotNull
  public KeyRole getRole() {
    return myRole;
  }

  public enum KeyRole {
    RETURN_TYPE,
    QUALIFIER;

    private static final DataExternalizer<KeyRole> DATA_EXTERNALIZER = new EnumDataDescriptor<KeyRole>(KeyRole.class);
  }


  public static KeyDescriptor<MethodUsageIndexKey> createKeyDescriptor() {
    final DataExternalizer<String> stringDataExternalizer = new EnumeratorStringDescriptor();
    return new KeyDescriptor<MethodUsageIndexKey>() {
      @Override
      public void save(final DataOutput out, final MethodUsageIndexKey value) throws IOException {
        stringDataExternalizer.save(out, value.getQualifiedClassName());
        KeyRole.DATA_EXTERNALIZER.save(out, value.getRole());
      }

      @Override
      public MethodUsageIndexKey read(final DataInput in) throws IOException {
        return new MethodUsageIndexKey(stringDataExternalizer.read(in), KeyRole.DATA_EXTERNALIZER.read(in));
      }

      @Override
      public int getHashCode(final MethodUsageIndexKey value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(final MethodUsageIndexKey val1, final MethodUsageIndexKey val2) {
        return val1.equals(val2);
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodUsageIndexKey)) return false;

    final MethodUsageIndexKey that = (MethodUsageIndexKey)o;

    if (!myQualifiedClassName.equals(that.myQualifiedClassName)) return false;
    if (myRole != that.myRole) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myQualifiedClassName.hashCode();
    result = 31 * result + myRole.hashCode();
    return result;
  }
}
