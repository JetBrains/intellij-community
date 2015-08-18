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
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.ShowContainerInfoHandler;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowContainerInfoAction extends BaseCodeInsightAction{
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new ShowContainerInfoHandler();
  }

  @Override
  @Nullable
  protected Editor getBaseEditor(@NotNull final DataContext dataContext, @NotNull final Project project) {
    return CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull final PsiFile file) {
    return LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file) instanceof TreeBasedStructureViewBuilder;
  }
}