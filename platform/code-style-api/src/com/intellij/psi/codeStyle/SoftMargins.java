// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class SoftMargins implements Cloneable {

  private @Unmodifiable List<Integer> myValues;

  // Serialization getter
  @SuppressWarnings("unused")
  public @Nullable String getSOFT_MARGINS() {
    return myValues != null ? toString() : null;
  }

  @SuppressWarnings("unused") // Serialization setter
  public void setSOFT_MARGINS(@Nullable String valueList) {
    if (valueList != null) {
      String[] values = valueList.split(",\\s*");
      List<Integer> newValues = new ArrayList<>(values.length);
      for (String value : values) {
        try {
          newValues.add(Integer.parseInt(value));
        }
        catch (NumberFormatException nfe) {
          newValues = null;
          break;
        }
      }
      if (newValues != null) {
        Collections.sort(newValues);
        newValues = Collections.unmodifiableList(newValues);
      }
      myValues = newValues;
    }
  }

  @NotNull
  List<Integer> getValues() {
    return myValues != null ? myValues : Collections.emptyList();
  }

  void setValues(List<Integer> values) {
    if (values != null) {
      myValues = ContainerUtil.sorted(values);
    }
    else {
      myValues = null;
    }
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Object clone() {
    SoftMargins copy = new SoftMargins();
    copy.setValues(myValues);
    return copy;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SoftMargins) {
      List<Integer> otherMargins = ((SoftMargins)obj).getValues();
      return otherMargins.equals(getValues());
    }
    return false;
  }

  @Override
  public String toString() {
    if (myValues == null) {
      return "";
    }
    return myValues.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  public void serializeInto(@NotNull Element element) {
    if (myValues != null && !myValues.isEmpty()) {
      XmlSerializer.serializeInto(this, element);
    }
  }

  public void deserializeFrom(@NotNull Element element) {
    XmlSerializer.deserializeInto(this, element);
  }
}
