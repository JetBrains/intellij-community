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
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * @since May 18, 2005
 */
public class ProjectJdkListRenderer extends ColoredListCellRendererWrapper {
  @Override
  public void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    if (value == null || value instanceof Sdk) {
      OrderEntryAppearanceService.getInstance().forJdk((Sdk)value, false, selected, true).customize(this);
    }
    else {
      final String str = value.toString();
      if (str != null) {
        append(str, selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      }
    }
  }
}
