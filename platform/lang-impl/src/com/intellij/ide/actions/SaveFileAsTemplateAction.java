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

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;

import java.util.Arrays;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class SaveFileAsTemplateAction extends AnAction{
  @Override
  public void actionPerformed(AnActionEvent e){
    Project project = assertNotNull(e.getData(CommonDataKeys.PROJECT));
    String fileText = assertNotNull(e.getData(PlatformDataKeys.FILE_TEXT));
    VirtualFile file = assertNotNull(e.getData(CommonDataKeys.VIRTUAL_FILE));
    String extension = StringUtil.notNullize(file.getExtension());
    String nameWithoutExtension = file.getNameWithoutExtension();
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    for(SaveFileAsTemplateHandler handler: Extensions.getExtensions(SaveFileAsTemplateHandler.EP_NAME)) {
      String textFromHandler = handler.getTemplateText(psiFile, fileText, nameWithoutExtension);
      if (textFromHandler != null) {
        fileText = textFromHandler;
        break;
      }
    }
    FileTemplateManager templateManager = FileTemplateManager.getInstance(project);
    FileTemplate[] templates = templateManager.getAllTemplates();
    FileTemplate template = FileTemplateUtil.createTemplate(nameWithoutExtension, extension, fileText, templates);

    FileTemplateConfigurable configurable = new FileTemplateConfigurable(project);
    configurable.setProportion(0.6f);
    configurable.setTemplate(template, FileTemplateManagerImpl.getInstanceImpl(project).getDefaultTemplateDescription());
    SaveFileAsTemplateDialog dialog = new SaveFileAsTemplateDialog(project, configurable);
    if (dialog.showAndGet()) {
      templateManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, Arrays.asList(ArrayUtil.append(templates, template)));
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(CommonDataKeys.VIRTUAL_FILE) != null && e.getData(PlatformDataKeys.FILE_TEXT) != null);
  }
}
