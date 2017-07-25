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
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.Function;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.jetbrains.jps.backwardRefs.LightRef.*;

public final class LightRefDescriptor implements KeyDescriptor<LightRef>, DifferentSerializableBytesImplyNonEqualityPolicy {
  private final Function<IOException, ? extends RuntimeException> myExceptionGenerator;

  public LightRefDescriptor(Function<IOException, ? extends RuntimeException> generator) {myExceptionGenerator = generator;}

  @Override
  public int getHashCode(LightRef value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(LightRef val1, LightRef val2) {
    return val1.equals(val2);
  }

  @Override
  public void save(@NotNull DataOutput out, LightRef value) throws IOException {
    value.save(out, myExceptionGenerator);
  }

  @Override
  public LightRef read(@NotNull DataInput in) throws IOException {
    final byte type = in.readByte();
    switch (type) {
      case CLASS_MARKER:
        return new JavaLightClassRef(DataInputOutputUtil.readINT(in));
      case ANONYMOUS_CLASS_MARKER:
        return new JavaLightAnonymousClassRef(DataInputOutputUtil.readINT(in));
      case METHOD_MARKER:
        return new JavaLightMethodRef(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
      case FIELD_MARKER:
        return new JavaLightFieldRef(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
      case FUN_EXPR_MARKER:
        return new JavaLightFunExprDef(DataInputOutputUtil.readINT(in));
    }
    throw new AssertionError();
  }
}
