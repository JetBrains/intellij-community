/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
*/
public class SdkListCellRenderer extends ColoredListCellRendererWrapper<Sdk> {
  private final String myNullText;
  private final boolean myShowHomePath;

  public SdkListCellRenderer(@NotNull String nullText) {
    this(nullText, false);
  }

  public SdkListCellRenderer(@NotNull String nullText, boolean showHomePath) {
    myNullText = nullText;
    myShowHomePath = showHomePath;
  }

  @Override
  protected void doCustomize(final JList list, final Sdk sdk, final int index, final boolean selected, final boolean hasFocus) {
    if (sdk != null) {
      setIcon(getSdkIcon(sdk));
      append(sdk.getName());
      if (myShowHomePath) {
        append(" (" + FileUtil.toSystemDependentName(StringUtil.notNullize(sdk.getHomePath())) + ")",
               selected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else {
      append(myNullText);
    }
  }

  protected Icon getSdkIcon(Sdk sdk) {
    return ((SdkType) sdk.getSdkType()).getIcon();
  }
}
