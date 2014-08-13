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
package com.intellij.ide.actions;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @deprecated API compatibility. Utility methods moved to NavigationUtil.
 * todo [neuro] REMOVE-ME when September Ends..
 * @author gregsh
 */
public class GotoRelatedFileAction {

  /**
   * @deprecated
   * @see com.intellij.codeInsight.navigation.NavigationUtil#getRelatedItemsPopup(java.util.List, String)
   */
  public static JBPopup createPopup(List<? extends GotoRelatedItem> items, final String title) {
    return NavigationUtil.getRelatedItemsPopup(items, title);
  }

  /**
   * @deprecated
   * @see com.intellij.codeInsight.navigation.NavigationUtil#collectRelatedItems(com.intellij.psi.PsiElement, com.intellij.openapi.actionSystem.DataContext)
   */
  public static List<GotoRelatedItem> getItems(@NotNull PsiElement contextElement, @Nullable DataContext dataContext) {
    return NavigationUtil.collectRelatedItems(contextElement, dataContext);
  }
}
