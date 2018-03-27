/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public class ParamAnnotation implements RW.Savable{
  public static final ParamAnnotation[] EMPTY_ARRAY = new ParamAnnotation[0];

  public final int paramIndex;
  @NotNull
  public final TypeRepr.ClassType type;

  public ParamAnnotation(int paramIndex, @NotNull TypeRepr.ClassType type) {
    this.paramIndex = paramIndex;
    this.type = type;
  }

  public ParamAnnotation(DataExternalizer<TypeRepr.ClassType> externalizer, DataInput in) {
    try {
      paramIndex = DataInputOutputUtil.readINT(in);
      type = externalizer.read(in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void save(DataOutput out) {
    try {
      DataInputOutputUtil.writeINT(out, paramIndex);
      type.save(out);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ParamAnnotation that = (ParamAnnotation)o;

    if (paramIndex != that.paramIndex) return false;
    if (!type.equals(that.type)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = paramIndex;
    result = 31 * result + type.hashCode();
    return result;
  }
}
