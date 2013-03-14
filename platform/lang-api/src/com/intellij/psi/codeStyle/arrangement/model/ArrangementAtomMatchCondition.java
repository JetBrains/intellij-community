/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.model;

import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates a single atom match condition, e.g. 'entry type is field' or 'entry has 'static' modifier' etc.
 * <p/>
 * Not thread-safe..
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 1:17 PM
 */
public class ArrangementAtomMatchCondition implements ArrangementMatchCondition {

  @NotNull private final ArrangementSettingsToken myType;
  @NotNull private final Object                   myValue;

  private boolean myInverted;

  public ArrangementAtomMatchCondition(@NotNull ArrangementSettingsToken type) {
    this(type, type);
  }
  
  public ArrangementAtomMatchCondition(@NotNull ArrangementSettingsToken type, @NotNull Object value) {
    myType = type;
    myValue = value;
  }

  @NotNull
  public ArrangementSettingsToken getType() {
    return myType;
  }

  @NotNull
  public Object getValue() {
    return myValue;
  }

  @Override
  public void invite(@NotNull ArrangementMatchConditionVisitor visitor) {
    visitor.visit(this);
  }

  public void setInverted(boolean inverted) {
    myInverted = inverted;
  }

  @Override
  public int hashCode() {
    int result = myType.hashCode();
    result = 31 * result + myValue.hashCode();
    result = 31 * result + (myInverted ? 1 : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArrangementAtomMatchCondition setting = (ArrangementAtomMatchCondition)o;

    if (myInverted != setting.myInverted) {
      return false;
    }
    if (!myType.equals(setting.myType)) {
      return false;
    }
    if (!myValue.equals(setting.myValue)) {
      return false;
    }

    return true;
  }

  @NotNull
  @Override
  public ArrangementAtomMatchCondition clone() {
    ArrangementAtomMatchCondition result = new ArrangementAtomMatchCondition(myType, myValue);
    result.setInverted(myInverted);
    return result;
  }

  @Override
  public String toString() {
    if (myType.equals(myValue)) {
      return String.format("%s%s", myInverted ? "not " : "", myType.getRepresentationValue());
    }
    else {
      return String.format("%s: %s%s", myType.getRepresentationValue(), myInverted ? "not " : "", myValue.toString().toLowerCase());
    }
  }
}
