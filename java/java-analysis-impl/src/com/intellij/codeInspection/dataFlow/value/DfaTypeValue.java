/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:32:01 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;

public class DfaTypeValue extends DfaValue {
  public static class Factory {
    private final Map<DfaPsiType,ArrayList<DfaTypeValue>> myCache = ContainerUtil.newHashMap();
    @NotNull
    private final DfaValueFactory myFactory;

    Factory(@NotNull DfaValueFactory factory) {
      myFactory = factory;
    }

    @NotNull
    DfaTypeValue createTypeValue(@NotNull DfaPsiType type, @NotNull Nullness nullness) {
      ArrayList<DfaTypeValue> conditions = myCache.get(type);
      if (conditions == null) {
        conditions = new ArrayList<>();
        myCache.put(type, conditions);
      }
      else {
        for (DfaTypeValue aType : conditions) {
          if (aType.myNullness == nullness) return aType;
        }
      }

      DfaTypeValue result = new DfaTypeValue(type, nullness, myFactory);
      conditions.add(result);
      return result;
    }

  }

  @NotNull
  private final DfaPsiType myType;
  @NotNull
  private final Nullness myNullness;

  private DfaTypeValue(@NotNull DfaPsiType type, @NotNull Nullness nullness, @NotNull DfaValueFactory factory) {
    super(factory);
    myType = type;
    myNullness = nullness;
  }

  @NotNull
  public DfaPsiType getDfaType() {
    return myType;
  }

  public boolean isNullable() {
    return myNullness == Nullness.NULLABLE;
  }

  public boolean isNotNull() {
    return myNullness == Nullness.NOT_NULL;
  }

  @NotNull
  public Nullness getNullness() {
    return myNullness;
  }

  @NonNls
  public String toString() {
    return myType + ", nullable=" + myNullness;
  }

}
