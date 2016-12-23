/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler.backwardRefs.view;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class TestCompilerReferenceServiceAction extends AnAction {
  public TestCompilerReferenceServiceAction(String text) {
    super(text);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor != null) {
      final PsiElement element = TargetElementUtil.getInstance()
        .findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED, editor.getCaretModel().getOffset());
      if (element != null) {
        startActionFor(element);
      }
      System.out.println(element);
    }
  }

  protected abstract void startActionFor(@NotNull PsiElement element);

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      presentation.setEnabledAndVisible(CompilerReferenceService.isEnabled() && ApplicationManagerEx.getApplicationEx().isInternal());
    }
  }
}
