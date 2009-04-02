/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.openapi.options.binding;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class ValueAccessor<V> {

  public abstract V getValue();
  public abstract void setValue(V value);
  public abstract Class<V> getType();

  public static ValueAccessor textFieldAccessor(final JTextField from) {
    return new ValueAccessor<String>() {
      public String getValue() {
        return from.getText();
      }

      public void setValue(String value) {
        from.setText(value);
      }

      public Class<String> getType() {
        return String.class;
      }
    };
  }

  public static ValueAccessor checkBoxAccessor(final JCheckBox from) {
    return new ValueAccessor<Boolean>() {

      public Boolean getValue() {
        return from.isSelected();
      }

      public void setValue(Boolean value) {
        from.setSelected(value.booleanValue());
      }

      public Class<Boolean> getType() {
        return Boolean.class;
      }
    };
  }

}
