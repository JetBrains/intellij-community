/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Time: 6:45:14 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class DfaNotNullValue extends DfaValue {
  public static class Factory {
    private final DfaNotNullValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaNotNullValue>> myStringToObject;
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaNotNullValue(factory);
      myStringToObject = new HashMap<String, ArrayList<DfaNotNullValue>>();
    }

    @NotNull
    public DfaValue create(@Nullable PsiType type) {
      if (type == null) return DfaUnknownValue.getInstance();
      mySharedInstance.myType = type;

      String id = mySharedInstance.toString();
      ArrayList<DfaNotNullValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaNotNullValue>();
        myStringToObject.put(id, conditions);
      }
      else {
        for (DfaNotNullValue value : conditions) {
          if (value.hardEquals(mySharedInstance)) return value;
        }
      }

      DfaNotNullValue result = new DfaNotNullValue(type, myFactory);
      conditions.add(result);
      return result;
    }
  }

  private PsiType myType;

  private DfaNotNullValue(PsiType myType, DfaValueFactory factory) {
    super(factory);
    this.myType = myType;
  }

  private DfaNotNullValue(DfaValueFactory factory) {
    super(factory);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "@notnull " + myType.getCanonicalText();
  }

  public PsiType getType() {
    return myType;
  }

  private boolean hardEquals(DfaNotNullValue aNotNull) {
    return aNotNull.myType == myType;
  }
}
