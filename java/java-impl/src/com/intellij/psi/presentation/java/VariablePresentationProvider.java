/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

import javax.swing.*;

/**
 * @author yole
 */
public class VariablePresentationProvider<T extends PsiVariable & NavigationItem> implements ItemPresentationProvider<T> {
  @Override
  public ItemPresentation getPresentation(final T variable) {
    return new ItemPresentation() {
      public String getPresentableText() {
        return PsiFormatUtil.formatVariable(variable, PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
      }

      public String getLocationString() {
        return "";
      }

      public Icon getIcon(boolean open) {
        return variable.getIcon(Iconable.ICON_FLAG_OPEN);
      }
    };
  }
}
