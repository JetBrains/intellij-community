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

import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.ide.fileTemplates.ui.ConfigureTemplatesDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class SaveFileAsTemplateAction extends AnAction{
  @Override
  public void actionPerformed(AnActionEvent e){
    Project project = e.getData(CommonDataKeys.PROJECT);
    String fileText = assertNotNull(e.getData(PlatformDataKeys.FILE_TEXT));
    VirtualFile file = assertNotNull(e.getData(CommonDataKeys.VIRTUAL_FILE));
    String extension = assertNotNull(file.getExtension());
    String nameWithoutExtension = file.getNameWithoutExtension();
    AllFileTemplatesConfigurable fileTemplateOptions = new AllFileTemplatesConfigurable();
    ConfigureTemplatesDialog dialog = new ConfigureTemplatesDialog(project, fileTemplateOptions);
    fileTemplateOptions.selectTemplatesTab();
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    for(SaveFileAsTemplateHandler handler: Extensions.getExtensions(SaveFileAsTemplateHandler.EP_NAME)) {
      String textFromHandler = handler.getTemplateText(psiFile, fileText, nameWithoutExtension);
      if (textFromHandler != null) {
        fileText = textFromHandler;
        break;
      }
    }
    fileTemplateOptions.createNewTemplate(nameWithoutExtension, extension, fileText);
    dialog.show();
  }

  @Override
  public void update(AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    String fileText = e.getData(PlatformDataKeys.FILE_TEXT);
    e.getPresentation().setEnabled(fileText != null && file != null && file.getExtension() != null);
  }
}
