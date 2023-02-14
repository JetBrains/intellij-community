// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.core.CoreBundle;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;


public class JavaFilePasteProvider implements PasteProvider {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void performPaste(@NotNull final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null) return;
    String copied = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    if (copied == null) return;
    String fileText = StringUtil.convertLineSeparators(copied);
    String className = detectClassName(project, fileText);
    if (className == null) return;
    final PsiDirectory targetDir = ideView.getOrChooseDirectory();
    if (targetDir == null) return;
    String fileName = className + ".java";
    PsiFile existingFile = targetDir.findFile(fileName);
    if (existingFile != null && 
        !MessageDialogBuilder.yesNo(IdeBundle.message("title.file.already.exists"),
                                    CoreBundle.message("prompt.overwrite.project.file", fileName, "")).ask(project)) {
      return;
    }
    WriteCommandAction.writeCommandAction(project).withName(
      JavaBundle.message("paste.class.command.name", className)).run(() -> {
      PsiFile file = existingFile;
      if (file == null) {
        try {
          file = targetDir.createFile(fileName);
        }
        catch (IncorrectOperationException e) {
          return;
        }
      }
      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document != null) {
        document.setText(fileText);
        PsiDocumentManager.getInstance(project).commitDocument(document);
      }
      if (file instanceof PsiJavaFile) {
        updatePackageStatement((PsiJavaFile)file, targetDir);
      }
      PsiNavigationSupport.getInstance().createNavigatable(project, file.getVirtualFile(), -1).navigate(true);
    });
  }

  private static void updatePackageStatement(final PsiJavaFile javaFile, final PsiDirectory targetDir) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDir);
    if (aPackage == null) return;
    final PsiPackageStatement oldStatement = javaFile.getPackageStatement();
    final Project project = javaFile.getProject();
    if ((oldStatement != null && !oldStatement.getPackageName().equals(aPackage.getQualifiedName()) ||
        (oldStatement == null && aPackage.getQualifiedName().length() > 0))) {
      CommandProcessor.getInstance().executeCommand(project, () -> {
        try {
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
          final PsiPackageStatement newStatement = factory.createPackageStatement(aPackage.getQualifiedName());
          if (oldStatement != null) {
            oldStatement.replace(newStatement);
          }
          else {
            final PsiElement addedStatement = javaFile.addAfter(newStatement, null);
            final TextRange textRange = addedStatement.getTextRange();
            // ensure line break is added after the statement
            CodeStyleManager.getInstance(project).reformatRange(javaFile, textRange.getStartOffset(), textRange.getEndOffset()+1);
          }
        }
        catch (IncorrectOperationException e) {
          // ignore
        }
      }, JavaBundle.message("command.name.updating.package.statement"), null);
    }
  }

  @Override
  public boolean isPastePossible(@NotNull final DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isPasteEnabled(@NotNull final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null || ideView.getDirectories().length == 0) {
      return false;
    }
    String pasteText = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    return pasteText != null && getPastedClasses(project, pasteText).length >= 1;
  }

  @Nullable
  static String detectClassName(@NotNull Project project, @NotNull String fileText) {
    final PsiClass[] classes = getPastedClasses(project, fileText);
    if (classes.length < 1) return null;
    for (PsiClass aClass : classes) {
      if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return aClass.getName();
      }
    }
    return classes[0].getName();
  }

  private static PsiClass @NotNull [] getPastedClasses(@NotNull Project project, @NotNull String pasteText) {
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(
      "A.java", JavaLanguage.INSTANCE, StringUtil.convertLineSeparators(pasteText), false, false);
    PsiUtil.FILE_LANGUAGE_LEVEL_KEY.set(psiFile, LanguageLevel.JDK_16); // to parse records
    return psiFile instanceof PsiJavaFile ? ((PsiJavaFile)psiFile).getClasses() : PsiClass.EMPTY_ARRAY;
  }
}
