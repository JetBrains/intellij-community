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
package com.intellij.ui;

import javax.swing.*;
import java.util.Map;

public class MappingListCellRenderer extends ListCellRendererWrapper {
  private final Map<Object, String> myValueMap;

  public MappingListCellRenderer(final ListCellRenderer original, final Map<Object, String> valueMap) {
    super();
    myValueMap = valueMap;
  }

  @Override
  public void customize(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
    final String newValue = myValueMap.get(value);
    if (newValue != null) {
      setText(newValue);
    }
  }
}
