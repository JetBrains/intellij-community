/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;

/**
 * @author Dmitry Batkovich
 */
public interface OptionAccessor {

  boolean getOption(String optionName);

  void setOption(String optionName, boolean optionValue);

  class Default implements OptionAccessor {
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ui.OptionAccessor");
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
