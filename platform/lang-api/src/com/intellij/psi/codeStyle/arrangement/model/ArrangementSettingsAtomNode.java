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

import org.jetbrains.annotations.NotNull;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 1:17 PM
 */
public class ArrangementSettingsAtomNode implements ArrangementSettingsNode {

  @NotNull private final ArrangementSettingType myType;
  @NotNull private final Object                 myValue;
  
  private boolean myInverted;

  public ArrangementSettingsAtomNode(@NotNull ArrangementSettingType type, @NotNull Object value) {
    myType = type;
    myValue = value;
  }

  @NotNull
  public ArrangementSettingType getType() {
    return myType;
  }

  @NotNull
  public Object getValue() {
    return myValue;
  }

  @Override
  public void invite(@NotNull ArrangementSettingsNodeVisitor visitor) {
    visitor.visit(this);
  }

  public boolean isInverted() {
    return myInverted;
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

    ArrangementSettingsAtomNode node = (ArrangementSettingsAtomNode)o;

    if (myInverted != node.myInverted) {
      return false;
    }
    if (myType != node.myType) {
      return false;
    }
    if (!myValue.equals(node.myValue)) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s: %s%s", myType.toString().toLowerCase(), myInverted ? "not " : "", myValue.toString().toLowerCase());
  }
}
