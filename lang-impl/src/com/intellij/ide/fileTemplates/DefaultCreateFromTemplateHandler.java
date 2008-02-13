package com.intellij.ide.fileTemplates;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;

import java.util.Properties;

/**
 * @author yole
 */
public class DefaultCreateFromTemplateHandler implements CreateFromTemplateHandler {
  public boolean handlesTemplate(final FileTemplate template) {
    return true;
  }

  public PsiElement createFromTemplate(final Project project, final PsiDirectory directory, String fileName, final FileTemplate template,
                                       final String templateText,
                                       final Properties props) throws IncorrectOperationException {
    final String suggestedFileNameEnd = "." + template.getExtension();

    if (!fileName.endsWith(suggestedFileNameEnd)) {
      fileName += suggestedFileNameEnd;
    }

    directory.checkCreateFile(fileName);
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, templateText);
    file = (PsiFile)directory.add(file);
    return file;
  }

  public boolean canCreate(final PsiDirectory[] dirs) {
    return true;
  }
}
