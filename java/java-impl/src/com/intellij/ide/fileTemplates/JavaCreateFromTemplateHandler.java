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
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

import java.util.Properties;

/**
 * @author yole
 */
public class JavaCreateFromTemplateHandler implements CreateFromTemplateHandler {
  public static PsiClass createClassOrInterface(Project project,
                                                PsiDirectory directory,
                                                String content,
                                                boolean reformat,
                                                String extension) throws IncorrectOperationException {
    if (extension == null) extension = StdFileTypes.JAVA.getDefaultExtension();
    final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("myclass" + "." + extension, content);
    if (!(psiFile instanceof PsiJavaFile)){
      throw new IncorrectOperationException("This template did not produce a Java class or an interface\n"+psiFile.getText());
    }
    PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
    final PsiClass[] classes = psiJavaFile.getClasses();
    if (classes.length == 0) {
      throw new IncorrectOperationException("This template did not produce a Java class or an interface\n"+psiFile.getText());
    }
    PsiClass createdClass = classes[0];
    if(reformat){
      CodeStyleManager.getInstance(project).reformat(psiJavaFile);
    }
    String className = createdClass.getName();
    String fileName = className + "." + extension;
    if(createdClass.isInterface()){
      JavaDirectoryService.getInstance().checkCreateInterface(directory, className);
    }
    else{
      JavaDirectoryService.getInstance().checkCreateClass(directory, className);
    }

    final LanguageLevel ll = JavaDirectoryService.getInstance().getLanguageLevel(directory);
    if (ll.compareTo(LanguageLevel.JDK_1_5) < 0) {
      if (createdClass.isAnnotationType()) {
        throw new IncorrectOperationException("Annotations only supported at language level 1.5 and higher");
      }

      if (createdClass.isEnum()) {
        throw new IncorrectOperationException("Enums only supported at language level 1.5 and higher");
      }
    }

    psiJavaFile = (PsiJavaFile)psiJavaFile.setName(fileName);
    PsiElement addedElement = directory.add(psiJavaFile);
    if (addedElement instanceof PsiJavaFile) {
      psiJavaFile = (PsiJavaFile)addedElement;

      return psiJavaFile.getClasses()[0];
    }
    else {
      PsiFile containingFile = addedElement.getContainingFile();
      throw new IncorrectOperationException("Selected class file name '" +
                                            containingFile.getName() +  "' mapped to not java file type '"+
                                            containingFile.getFileType().getDescription() + "'");
    }
  }

  static void hackAwayEmptyPackage(PsiJavaFile file, FileTemplate template, Properties props) throws IncorrectOperationException {
    if (!template.isTemplateOfType(StdFileTypes.JAVA)) return;

    String packageName = props.getProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME);
    if(packageName == null || packageName.length() == 0 || packageName.equals(FileTemplate.ATTRIBUTE_PACKAGE_NAME)){
      PsiPackageStatement packageStatement = file.getPackageStatement();
      if (packageStatement != null) {
        packageStatement.delete();
      }
    }
  }

  public boolean handlesTemplate(final FileTemplate template) {
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
    return fileType.equals(StdFileTypes.JAVA);
  }

  public PsiElement createFromTemplate(final Project project, final PsiDirectory directory, final String fileName, FileTemplate template,
                                       String templateText, Properties props) throws IncorrectOperationException {
    String extension = template.getExtension();
    PsiElement result = createClassOrInterface(project, directory, templateText, template.isReformatCode(), extension);
    hackAwayEmptyPackage((PsiJavaFile)result.getContainingFile(), template, props);
    return result;
  }

  public boolean canCreate(final PsiDirectory[] dirs) {
    return false;
  }

  public static boolean canCreate(PsiDirectory dir) {
    return JavaDirectoryService.getInstance().getPackage(dir) != null;
  }
}
