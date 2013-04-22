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

package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.copy.CopyHandler;

public class CloneElementAction extends CopyElementAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CloneElementAction");

  @Override
  protected void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    LOG.assertTrue(elements.length == 1);
    CopyHandler.doClone(elements[0]);
  }

  @Override
  protected void updateForEditor(DataContext dataContext, Presentation presentation) {
    super.updateForEditor(dataContext, presentation);
    presentation.setVisible(false);
  }

  @Override
  protected void updateForToolWindow(String id, DataContext dataContext,Presentation presentation) {
    // work only with single selection
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    presentation.setEnabled(elements != null && elements.length == 1 && CopyHandler.canClone(elements));
    presentation.setVisible(true);
    if (!ToolWindowId.COMMANDER.equals(id)) {
      presentation.setVisible(false);
    }
  }
}
