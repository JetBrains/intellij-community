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

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ColoredListCellRenderer;

import javax.swing.*;

/**
 * @author yole
*/
public class SdkListCellRenderer extends ColoredListCellRenderer {
  private String myNullText = "";
  private boolean myShowHomePath;

  public SdkListCellRenderer() {
  }

  public SdkListCellRenderer(final String nullText) {
    myNullText = nullText;
  }

  public SdkListCellRenderer(String nullText, boolean showHomePath) {
    myNullText = nullText;
    myShowHomePath = showHomePath;
  }

  protected void customizeCellRenderer(final JList list,
                                       final Object value,
                                       final int index,
                                       final boolean selected,
                                       final boolean hasFocus) {
    final Sdk sdk = (Sdk) value;
    if (sdk != null) {
      // icon
      setIcon(sdk.getSdkType().getIcon());
      // text
      append(sdk.getName());
      if (myShowHomePath) {
        append(" (" + FileUtil.toSystemDependentName(sdk.getHomePath()) + ")");
      }
    }
    else {
      append(myNullText);
    }
  }
}
