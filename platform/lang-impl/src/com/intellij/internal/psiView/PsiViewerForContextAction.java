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
package com.intellij.internal.psiView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.DumbAware;

/**
 * @author Nadya.Zabrodina
 */
public class PsiViewerForContextAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext ctx = e.getDataContext();
    new PsiViewerDialog(e.getProject(), false, CommonDataKeys.PSI_FILE.getData(ctx), CommonDataKeys.EDITOR.getData(ctx)).show();
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = ApplicationManagerEx.getApplicationEx().isInternal() && e.getProject() != null;
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled && CommonDataKeys.PSI_FILE.getData(e.getDataContext()) != null);
  }
}