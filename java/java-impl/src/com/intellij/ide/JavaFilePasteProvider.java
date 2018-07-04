/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;

/**
 * @author yole
 */
public class JavaFilePasteProvider implements PasteProvider {
  public void performPaste(@NotNull final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null) return;
    final PsiJavaFile javaFile = createJavaFileFromClipboardContent(project);
    if (javaFile == null) return;
    final PsiClass[] classes = javaFile.getClasses();
    if (classes.length < 1) return;
    final PsiDirectory targetDir = ideView.getOrChooseDirectory();
    if (targetDir == null) return;
    PsiClass publicClass = classes[0];
    for (PsiClass aClass : classes) {
      if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        publicClass = aClass;
        break;
      }
    }
    final PsiClass mainClass = publicClass;
    WriteCommandAction.writeCommandAction(project).withName("Paste class '" + mainClass.getName() + "'").run(() -> {
      PsiFile file;
      try {
        file = targetDir.createFile(mainClass.getName() + ".java");
      }
      catch (IncorrectOperationException e) {
        return;
      }
      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document != null) {
        document.setText(javaFile.getText());
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
          PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
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
      }, "Updating package statement", null);
    }
  }

  public boolean isPastePossible(@NotNull final DataContext dataContext) {
    return true;
  }

  public boolean isPasteEnabled(@NotNull final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null || ideView.getDirectories().length == 0) {
      return false;
    }
    PsiJavaFile file = createJavaFileFromClipboardContent(project);
    return file != null && file.getClasses().length >= 1;
  }

  @Nullable
  private static PsiJavaFile createJavaFileFromClipboardContent(final Project project) {
    String text = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    if (text == null) return null;
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("A.java", JavaLanguage.INSTANCE, 
                                                                             StringUtil.convertLineSeparators(text));
    return psiFile instanceof PsiJavaFile ? (PsiJavaFile)psiFile : null;
  }
}
