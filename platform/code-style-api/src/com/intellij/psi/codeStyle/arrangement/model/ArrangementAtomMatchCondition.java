// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.model;

import com.intellij.CodeStyleBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.InvertibleArrangementSettingsToken;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates a single atom match condition, e.g. 'entry type is field' or 'entry has 'static' modifier' etc.
 * <p/>
 * Not thread-safe..
 */
public class ArrangementAtomMatchCondition implements ArrangementMatchCondition {

  private final @NotNull ArrangementSettingsToken myType;
  private final @NotNull Object                   myValue;

  public ArrangementAtomMatchCondition(@NotNull ArrangementSettingsToken type) {
    this(type, type instanceof InvertibleArrangementSettingsToken ? Boolean.TRUE : type);
  }
  
  public ArrangementAtomMatchCondition(@NotNull ArrangementSettingsToken type, @NotNull Object value) {
    myType = type;
    myValue = value;
  }

  public @NotNull ArrangementSettingsToken getType() {
    return myType;
  }

  public @NotNull Object getValue() {
    return myValue;
  }

  @Override
  public void invite(@NotNull ArrangementMatchConditionVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = myType.hashCode();
    result = 31 * result + myValue.hashCode();
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

    if (!myType.equals(setting.myType)) {
      return false;
    }
    if (!myValue.equals(setting.myValue)) {
      return false;
    }

    return true;
  }

  @Override
  public @NotNull ArrangementAtomMatchCondition clone() {
    return new ArrangementAtomMatchCondition(myType, myValue);
  }

  @Override
  public String toString() {
    return getPresentableValue();
  }

  public @NotNull @Nls String getPresentableValue() {
    if (myValue instanceof Boolean) {
      return (Boolean)myValue ? myType.getRepresentationValue()
                              : CodeStyleBundle.message("arrangement.settings.text.condition.not", myType.getRepresentationValue());
    }
    else {
      //noinspection HardCodedStringLiteral
      return String.format("%s: %s", myType.getRepresentationValue(), StringUtil.toLowerCase(myValue.toString()));
    }
  }
}
