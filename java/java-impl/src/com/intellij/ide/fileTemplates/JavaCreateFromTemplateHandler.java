// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;


public class JavaCreateFromTemplateHandler implements CreateFromTemplateHandler {
  public static PsiClass createClassOrInterface(Project project,
                                                PsiDirectory directory,
                                                String content,
                                                boolean reformat,
                                                String extension) throws IncorrectOperationException {
    return createClassOrInterface(project, directory, content, reformat, extension, null);
  }

  private static PsiClass createClassOrInterface(Project project,
                                                 PsiDirectory directory,
                                                 String content,
                                                 boolean reformat,
                                                 String extension, @Nullable String optionalClassName) throws IncorrectOperationException {
    if (extension == null) extension = JavaFileType.INSTANCE.getDefaultExtension();
    final String name = "myClass" + "." + extension;
    final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(name, JavaLanguage.INSTANCE, content, false, false);
    LanguageLevel highest = LanguageLevel.HIGHEST;
    LanguageLevel implicitClassesMinimumLevel = JavaFeature.IMPLICIT_CLASSES.getMinimumLevel();
    if (highest.isLessThan(implicitClassesMinimumLevel)) {
      highest = implicitClassesMinimumLevel;
    }
    psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, highest);

    if (!(psiFile instanceof PsiJavaFile psiJavaFile)){
      throw new IncorrectOperationException("This template did not produce a Java class or an interface\n"+psiFile.getText());
    }
    final PsiClass[] classes = psiJavaFile.getClasses();
    if (classes.length == 0) {
      throw new IncorrectOperationException("This template did not produce a Java class or an interface\n"+psiFile.getText());
    }
    PsiClass createdClass = classes[0];
    String className;
    if (optionalClassName != null && createdClass instanceof PsiImplicitClass) {
      className = optionalClassName;
    }
    else {
      className = createdClass.getName();
    }
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
      throw new IncorrectOperationException("The file '" + containingFile +  "' was expected to be of JAVA file type, but got: '"+ containingFile.getFileType() + "'");
    }
  }

  static void hackAwayEmptyPackage(PsiJavaFile file, FileTemplate template, Map<String, Object> props) throws IncorrectOperationException {
    if (!template.isTemplateOfType(JavaFileType.INSTANCE)) return;

    String packageName = (String)props.get(FileTemplate.ATTRIBUTE_PACKAGE_NAME);
    if(packageName == null || packageName.isEmpty() || packageName.equals(FileTemplate.ATTRIBUTE_PACKAGE_NAME)){
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

  @Override
  public @NotNull PsiElement createFromTemplate(@NotNull Project project,
                                                @NotNull PsiDirectory directory,
                                                String fileName,
                                                @NotNull FileTemplate template,
                                                @NotNull String templateText,
                                                @NotNull Map<String, Object> props) throws IncorrectOperationException {
    String extension = template.getExtension();
    String name = null;
    if (props.get(FileTemplate.ATTRIBUTE_NAME) instanceof String optionalName) {
      name = optionalName;
    }
    PsiElement result = createClassOrInterface(project, directory, templateText, template.isReformatCode(), extension, name);
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

  @Override
  public @NotNull String getErrorMessage() {
    return JavaBundle.message("title.cannot.create.class");
  }

  @Override
  public void prepareProperties(@NotNull Map<String, Object> props) {
    String packageName = (String)props.get(FileTemplate.ATTRIBUTE_PACKAGE_NAME);
    if (packageName == null || packageName.isEmpty()) {
      props.put(FileTemplate.ATTRIBUTE_PACKAGE_NAME, FileTemplate.ATTRIBUTE_PACKAGE_NAME);
    }
  }

  @Override
  public @NotNull String commandName(@NotNull FileTemplate template) {
    return JavaBundle.message("command.create.class.from.template");
  }

  public static boolean canCreate(PsiDirectory dir) {
    return JavaDirectoryService.getInstance().getPackage(dir) != null;
  }
}