/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

  @Unmodifiable
  private List<Integer> myValues;

  @SuppressWarnings("unused") // Serialization getter
  @Nullable
  public String getSOFT_MARGINS() {
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
    if (myValues != null && myValues.size() > 0) {
      XmlSerializer.serializeInto(this, element);
    }
  }

  public void deserializeFrom(@NotNull Element element) {
    XmlSerializer.deserializeInto(this, element);
  }
}
