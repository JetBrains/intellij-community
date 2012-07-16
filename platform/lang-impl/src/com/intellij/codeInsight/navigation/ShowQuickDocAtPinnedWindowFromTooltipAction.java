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
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 7/13/12 11:43 AM
 */
public class ShowQuickDocAtPinnedWindowFromTooltipAction extends ShowQuickDocFromTooltipAction {

  public ShowQuickDocAtPinnedWindowFromTooltipAction() {
    super(AllIcons.General.Pin_tab);
  }

  @Override
  protected void doActionPerformed(@NotNull Pair<PsiElement, PsiElement> docInfo, @NotNull DocumentationManager docManager) {
    docManager.createToolWindow(docInfo.first, docInfo.second);
  }
}
