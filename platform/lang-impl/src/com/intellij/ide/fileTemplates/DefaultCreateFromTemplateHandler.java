// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public class DefaultCreateFromTemplateHandler implements CreateFromTemplateHandler {
  @Override
  public boolean handlesTemplate(@NotNull final FileTemplate template) {
    return true;
  }

  @NotNull
  @Override
  public PsiElement createFromTemplate(@NotNull final Project project, @NotNull final PsiDirectory directory, String fileName, @NotNull final FileTemplate template,
                                       @NotNull final String templateText,
                                       @NotNull final Map<String, Object> props) throws IncorrectOperationException {
    fileName = checkAppendExtension(fileName, template);

    if (FileTypeManager.getInstance().isFileIgnored(fileName)) {
      throw new IncorrectOperationException("This filename is ignored (Settings | Editor | File Types | Ignore files and folders)");
    }

    directory.checkCreateFile(fileName);
    FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, type, templateText);

    file = (PsiFile)directory.add(file);
    if (template.isReformatCode()) {
      CodeStyleManager.getInstance(project).scheduleReformatWhenSettingsComputed(file);
    }
    return file;
  }

  protected String checkAppendExtension(String fileName, @NotNull FileTemplate template) {
    final String suggestedFileNameEnd = "." + template.getExtension();

    if (!fileName.endsWith(suggestedFileNameEnd)) {
      fileName += suggestedFileNameEnd;
    }
    return fileName;
  }

  @Override
  public boolean canCreate(final PsiDirectory @NotNull [] dirs) {
    return true;
  }

  @Override
  public boolean isNameRequired() {
    return true;
  }

  @NotNull
  @Override
  public String getErrorMessage() {
    return IdeBundle.message("title.cannot.create.file");
  }

  @Override
  public void prepareProperties(@NotNull Map<String, Object> props,
                                String filename,
                                @NotNull FileTemplate template,
                                @NotNull Project project) {
    String fileName = checkAppendExtension(filename, template);
    props.put(FileTemplate.ATTRIBUTE_FILE_NAME, fileName);
  }

  @Override
  public void prepareProperties(@NotNull Map<String, Object> props) {
    // ignore
  }
}
