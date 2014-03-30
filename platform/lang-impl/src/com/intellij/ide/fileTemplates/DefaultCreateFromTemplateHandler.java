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

package com.intellij.ide.fileTemplates;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

/**
 * @author yole
 */
public class DefaultCreateFromTemplateHandler implements CreateFromTemplateHandler {
  @Override
  public boolean handlesTemplate(final FileTemplate template) {
    return true;
  }

  @Override
  public PsiElement createFromTemplate(final Project project, final PsiDirectory directory, String fileName, final FileTemplate template,
                                       final String templateText,
                                       final Map<String, Object> props) throws IncorrectOperationException {
    fileName = checkAppendExtension(fileName, template);

    if (FileTypeManager.getInstance().isFileIgnored(fileName)) {
      throw new IncorrectOperationException("This filename is ignored (Settings | File Types | Ignore files and folders)");
    }

    directory.checkCreateFile(fileName);
    FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, type, templateText);

    if (template.isReformatCode()) {
      CodeStyleManager.getInstance(project).reformat(file);
    }

    file = (PsiFile)directory.add(file);
    return file;
  }

  protected String checkAppendExtension(String fileName, final FileTemplate template) {
    final String suggestedFileNameEnd = "." + template.getExtension();

    if (!fileName.endsWith(suggestedFileNameEnd)) {
      fileName += suggestedFileNameEnd;
    }
    return fileName;
  }

  @Override
  public boolean canCreate(final PsiDirectory[] dirs) {
    return true;
  }

  @Override
  public boolean isNameRequired() {
    return true;
  }

  @Override
  public String getErrorMessage() {
    return IdeBundle.message("title.cannot.create.file");
  }

  @Override
  public void prepareProperties(Map<String, Object> props) {
    // ignore
  }
}
