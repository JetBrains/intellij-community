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
package com.intellij.ide.fileTemplates;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author yole
 */
public class JavaCreateFromTemplateHandler implements CreateFromTemplateHandler {
  public static PsiClass createClassOrInterface(Project project,
                                                PsiDirectory directory,
                                                String content,
                                                boolean reformat,
                                                String extension) throws IncorrectOperationException {
    if (extension == null) extension = JavaFileType.INSTANCE.getDefaultExtension();
    final String name = "myClass" + "." + extension;
    final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(name, JavaLanguage.INSTANCE, content, false, false);
    psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.JDK_15_PREVIEW);

    if (!(psiFile instanceof PsiJavaFile)){
      throw new IncorrectOperationException("This template did not produce a Java class or an interface\n"+psiFile.getText());
    }
    PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
    final PsiClass[] classes = psiJavaFile.getClasses();
    if (classes.length == 0) {
      throw new IncorrectOperationException("This template did not produce a Java class or an interface\n"+psiFile.getText());
    }
    PsiClass createdClass = classes[0];
    String className = createdClass.getName();
    JavaDirectoryServiceImpl.checkCreateClassOrInterface(directory, className);

    final LanguageLevel ll = JavaDirectoryService.getInstance().getLanguageLevel(directory);
    if (ll.compareTo(LanguageLevel.JDK_1_5) < 0) {
      if (createdClass.isAnnotationType()) {
        throw new IncorrectOperationException("Annotations only supported at language level 1.5 and higher");
      }

      if (createdClass.isEnum()) {
        throw new IncorrectOperationException("Enums only supported at language level 1.5 and higher");
      }
    }

    psiJavaFile = (PsiJavaFile)psiJavaFile.setName(className + "." + extension);
    PsiElement addedElement = directory.add(psiJavaFile);
    if (addedElement instanceof PsiJavaFile) {
      psiJavaFile = (PsiJavaFile)addedElement;
      if(reformat){
        CodeStyleManager.getInstance(project).scheduleReformatWhenSettingsComputed(psiJavaFile);
      }

      return psiJavaFile.getClasses()[0];
    }
    else {
      PsiFile containingFile = addedElement.getContainingFile();
      throw new IncorrectOperationException("Selected class file name '" +
                                            containingFile.getName() +  "' mapped to not java file type '"+
                                            containingFile.getFileType().getDescription() + "'");
    }
  }

  static void hackAwayEmptyPackage(PsiJavaFile file, FileTemplate template, Map<String, Object> props) throws IncorrectOperationException {
    if (!template.isTemplateOfType(JavaFileType.INSTANCE)) return;

    String packageName = (String)props.get(FileTemplate.ATTRIBUTE_PACKAGE_NAME);
    if(packageName == null || packageName.length() == 0 || packageName.equals(FileTemplate.ATTRIBUTE_PACKAGE_NAME)){
      PsiPackageStatement packageStatement = file.getPackageStatement();
      if (packageStatement != null) {
        packageStatement.delete();
      }
    }
  }

  @Override
  public boolean handlesTemplate(@NotNull FileTemplate template) {
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
    return fileType.equals(JavaFileType.INSTANCE) && !ArrayUtil.contains(template.getName(), JavaTemplateUtil.INTERNAL_FILE_TEMPLATES);
  }

  @NotNull
  @Override
  public PsiElement createFromTemplate(@NotNull Project project,
                                       @NotNull PsiDirectory directory,
                                       String fileName,
                                       @NotNull FileTemplate template,
                                       @NotNull String templateText,
                                       @NotNull Map<String, Object> props) throws IncorrectOperationException {
    String extension = template.getExtension();
    PsiElement result = createClassOrInterface(project, directory, templateText, template.isReformatCode(), extension);
    hackAwayEmptyPackage((PsiJavaFile)result.getContainingFile(), template, props);
    return result;
  }

  @Override
  public boolean canCreate(final PsiDirectory @NotNull [] dirs) {
    for (PsiDirectory dir : dirs) {
      if (canCreate(dir)) return true;
    }
    return false;
  }

  @Override
  public boolean isNameRequired() {
    return false;
  }

  @NotNull
  @Override
  public String getErrorMessage() {
    return JavaBundle.message("title.cannot.create.class");
  }

  @Override
  public void prepareProperties(@NotNull Map<String, Object> props) {
    String packageName = (String)props.get(FileTemplate.ATTRIBUTE_PACKAGE_NAME);
    if (packageName == null || packageName.length() == 0) {
      props.put(FileTemplate.ATTRIBUTE_PACKAGE_NAME, FileTemplate.ATTRIBUTE_PACKAGE_NAME);
    }
  }

  @NotNull
  @Override
  public String commandName(@NotNull FileTemplate template) {
    return JavaBundle.message("command.create.class.from.template");
  }

  public static boolean canCreate(PsiDirectory dir) {
    return JavaDirectoryService.getInstance().getPackage(dir) != null;
  }
}