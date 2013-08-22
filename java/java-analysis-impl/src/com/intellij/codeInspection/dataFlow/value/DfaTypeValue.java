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
 * Time: 6:32:01 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class DfaTypeValue extends DfaValue {
  public static class Factory {
    private final DfaTypeValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaTypeValue>> myStringToObject;
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaTypeValue(factory);
      myStringToObject = new HashMap<String, ArrayList<DfaTypeValue>>();
    }

    @NotNull
    public DfaTypeValue create(@NotNull PsiType type, boolean nullable) {
      type = TypeConversionUtil.erasure(type);
      mySharedInstance.myType = type;
      mySharedInstance.myCanonicalText = StringUtil.notNullize(type.getCanonicalText(), PsiKeyword.NULL);
      mySharedInstance.myIsNullable = nullable;

      String id = mySharedInstance.toString();
      ArrayList<DfaTypeValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaTypeValue>();
        myStringToObject.put(id, conditions);
      } else {
        for (DfaTypeValue aType : conditions) {
          if (aType.hardEquals(mySharedInstance)) return aType;
        }
      }

      DfaTypeValue result = new DfaTypeValue(type, nullable, myFactory, mySharedInstance.myCanonicalText);
      conditions.add(result);
      return result;
    }

    public DfaTypeValue create(@NotNull PsiType type) {
      return create(type, false);
    }
  }

  private PsiType myType;
  private String myCanonicalText;
  private boolean myIsNullable;

  private DfaTypeValue(DfaValueFactory factory) {
    super(factory);
  }

  private DfaTypeValue(PsiType type, boolean isNullable, DfaValueFactory factory, String canonicalText) {
    super(factory);
    myType = type;
    myIsNullable = isNullable;
    myCanonicalText = canonicalText;
  }

  public PsiType getType() {
    return myType;
  }

  public boolean isNullable() {
    return myIsNullable;
  }

  @NonNls
  public String toString() {
    return myCanonicalText + ", nullable=" + myIsNullable;
  }

  private boolean hardEquals(DfaTypeValue aType) {
    return Comparing.equal(myCanonicalText, aType.myCanonicalText) && myIsNullable == aType.myIsNullable;
  }

  public boolean isAssignableFrom(DfaTypeValue dfaType) {
    return dfaType != null && myType.isAssignableFrom(dfaType.myType);
  }

  public boolean isConvertibleFrom(DfaTypeValue dfaType) {
    if (dfaType == null) return false;
    assert myType.isValid() : "my type invalid";
    assert dfaType.myType.isValid() : " their type invalid";
    return myType.isConvertibleFrom(dfaType.myType);
  }
}
