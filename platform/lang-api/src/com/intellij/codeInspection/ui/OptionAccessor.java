// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.util.ReflectionUtil;

/**
 * @author Dmitry Batkovich
 */
public interface OptionAccessor {

  boolean getOption(String optionName);

  void setOption(String optionName, boolean optionValue);

  class Default implements OptionAccessor {
    private final InspectionProfileEntry myInspection;

    public Default(final InspectionProfileEntry inspection) {
      myInspection = inspection;
    }

    @Override
    public boolean getOption(final String optionName) {
      return ReflectionUtil.getField(myInspection.getClass(), myInspection, boolean.class, optionName);
    }

    @Override
    public void setOption(final String optionName, boolean optionValue) {
      ReflectionUtil.setField(myInspection.getClass(), myInspection, boolean.class, optionName, optionValue);
    }
  }
}
