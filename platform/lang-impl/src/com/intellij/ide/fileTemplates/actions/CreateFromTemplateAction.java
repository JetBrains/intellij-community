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

package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public class CreateFromTemplateAction extends CreateFromTemplateActionBase {
  private final Supplier<FileTemplate> myTemplate;

  /** Avoid calling the constructor from normal IDE actions, because:
   *  - Normal actions are preloaded at startup
   *  - Accessing FileTemplate out of FileTemplateManager triggers costly initialization
   */
  public CreateFromTemplateAction(@NotNull FileTemplate template) {
    this(template.getName(), FileTemplateUtil.getIcon(template), () -> template);
  }

  public CreateFromTemplateAction(String templateName, @Nullable Icon icon, @NotNull Supplier<FileTemplate> template){
    super(templateName, null, icon);
    myTemplate = template;
  }

  @Override
  protected FileTemplate getTemplate(final Project project, final PsiDirectory dir) {
    return myTemplate.get();
  }

  @Override
  public void update(AnActionEvent e){
    super.update(e);
    Presentation presentation = e.getPresentation();
    boolean isEnabled = CreateFromTemplateGroup.canCreateFromTemplate(e, myTemplate.get());
    presentation.setEnabled(isEnabled);
    presentation.setVisible(isEnabled);
  }

  public FileTemplate getTemplate() {
    return myTemplate.get();
  }
}
