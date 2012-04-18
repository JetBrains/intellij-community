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

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

/**
 * @author yole
*/
public class SdkListCellRenderer extends HtmlListCellRenderer<Sdk> {
  private String myNullText = "";
  private boolean myShowHomePath;

  public SdkListCellRenderer(final ListCellRenderer listCellRenderer) {
    super(listCellRenderer);
  }

  public SdkListCellRenderer(final String nullText, final ListCellRenderer listCellRenderer) {
    super(listCellRenderer);
    myNullText = nullText;
  }

  public SdkListCellRenderer(final String nullText, final boolean showHomePath, final ListCellRenderer listCellRenderer) {
    super(listCellRenderer);
    myNullText = nullText;
    myShowHomePath = showHomePath;
  }

  @Override
  protected void doCustomize(final JList list, final Sdk sdk, final int index, final boolean selected, final boolean hasFocus) {
    if (sdk != null) {
      // icon
      setIcon(getSdkIcon(sdk));
      // text
      append(sdk.getName());
      if (myShowHomePath) {
        append(" (" + FileUtil.toSystemDependentName(sdk.getHomePath()) + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else {
      append(myNullText);
    }
  }

  protected Icon getSdkIcon(Sdk sdk) {
    return sdk.getSdkType().getIcon();
  }
}
