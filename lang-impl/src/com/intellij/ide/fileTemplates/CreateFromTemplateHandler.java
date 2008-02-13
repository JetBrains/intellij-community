package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

import java.util.Properties;

/**
 * @author yole
 */
public interface CreateFromTemplateHandler {
  ExtensionPointName<CreateFromTemplateHandler> EP_NAME = ExtensionPointName.create("com.intellij.createFromTemplateHandler");

  boolean handlesTemplate(FileTemplate template);
  PsiElement createFromTemplate(Project project, PsiDirectory directory, final String fileName, FileTemplate template, String templateText,
                                Properties props) throws IncorrectOperationException;

  boolean canCreate(final PsiDirectory[] dirs);
}
