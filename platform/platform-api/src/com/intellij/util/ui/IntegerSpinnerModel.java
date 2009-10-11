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
package com.intellij.util.ui;

import javax.swing.*;



class IntegerSpinnerModel extends AbstractSpinnerModel {
  private final int myMinValue;
  private final int myMaxValue;
  private int myValue = 0;

  public IntegerSpinnerModel(int myMinValue, int myMaxValue) {
    this.myMinValue = myMinValue;
    this.myMaxValue = myMaxValue;
  }

  public Object getNextValue() {
    int result;
    if (myMaxValue >= 0 && myValue == myMaxValue) {
      if (myMinValue < 0) return null;
      result = myMinValue;
    } else {
      result = myValue + 1;
    }

    return new Integer(result);
  }

  public Object getPreviousValue() {
    int result;
    if (myMinValue >= 0 && myValue == myMinValue) {
      if (myMaxValue < 0) return null;
      result = myMaxValue;
    } else {
      result = myValue - 1;
    }
    return new Integer(result);
  }

  public Object getValue() {
    return String.valueOf(myValue);
  }

  public void setValue(Object value) {
    if (value instanceof String) setStringValue((String) value);
    if (value instanceof Integer) setIntegerValue((Integer) value);
    fireStateChanged();
  }

  private void setIntegerValue(Integer integer) {
    myValue = ((Integer) integer).intValue();
  }

  private void setStringValue(String s) {
    myValue = Integer.parseInt(s);
  }

  public int getIntValue() {
    return myValue;
  }

}

