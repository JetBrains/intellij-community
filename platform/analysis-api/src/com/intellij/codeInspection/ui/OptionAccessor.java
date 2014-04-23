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

import java.lang.reflect.Field;

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
      try {
        final Class<? extends InspectionProfileEntry> aClass = myInspection.getClass();
        final Field field = aClass.getField(optionName);
        return field.getBoolean(myInspection);
      } catch (IllegalAccessException ignored) {
        return false;
      } catch (NoSuchFieldException ignored) {
        return false;
      }
    }

    @Override
    public void setOption(final String optionName, boolean optionValue) {
      try {
        final Class<? extends InspectionProfileEntry> aClass = myInspection.getClass();
        final Field field = aClass.getField(optionName);
        field.setBoolean(myInspection, optionValue);
      } catch (IllegalAccessException ignored) {
        // nothing
      } catch (NoSuchFieldException ignored) {
        // nothing
      }
    }
  }

}
