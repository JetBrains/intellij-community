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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author lambdamix
 */
public class EquationExternalizer implements DataExternalizer<Collection<IntIdEquation>> {
  @Override
  public void save(@NotNull DataOutput out, Collection<IntIdEquation> equations) throws IOException {
    DataInputOutputUtil.writeINT(out, equations.size());
    for (IntIdEquation equation : equations) {
      out.write(equation.id);
      IntIdResult rhs = equation.rhs;
      if (rhs instanceof IntIdFinal) {
        IntIdFinal finalResult = (IntIdFinal)rhs;
        out.writeBoolean(true);
        DataInputOutputUtil.writeINT(out, finalResult.value.ordinal());
      } else {
        IntIdPending pendResult = (IntIdPending)rhs;
        out.writeBoolean(false);
        DataInputOutputUtil.writeINT(out, pendResult.infinum.ordinal());
        out.writeInt(pendResult.delta.length);
        for (IntIdComponent component : pendResult.delta) {
          int[] ids = component.ids;
          DataInputOutputUtil.writeINT(out, ids.length);
          for (int id : ids) {
            out.writeInt(id);
          }
        }
      }
    }
  }

  @Override
  public Collection<IntIdEquation> read(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    ArrayList<IntIdEquation> result = new ArrayList<IntIdEquation>(size);

    for (int x = 0; x < size; x++) {
      int equationId = in.readInt();
      boolean isFinal = in.readBoolean();
      if (isFinal) {
        int ordinal = DataInputOutputUtil.readINT(in);
        Value value = Value.values()[ordinal];
        result.add(new IntIdEquation(equationId, new IntIdFinal(value)));
      } else {
        int ordinal = DataInputOutputUtil.readINT(in);
        Value value = Value.values()[ordinal];
        int componentsNumber = DataInputOutputUtil.readINT(in);
        IntIdComponent[] components = new IntIdComponent[componentsNumber];
        for (int i = 0; i < componentsNumber; i++) {
          int componentSize = DataInputOutputUtil.readINT(in);
          int[] ids = new int[componentSize];
          for (int j = 0; j < componentSize; j++) {
            ids[j] = in.readInt();
          }
          components[i] = new IntIdComponent(ids);
        }
        result.add(new IntIdEquation(equationId, new IntIdPending(value, components)));
      }
    }

    return result;
  }
}
