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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveClassToSeparateFileFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(MoveClassToSeparateFileFix.class);

  private final PsiClass myClass;

  public MoveClassToSeparateFileFix(@NotNull PsiClass aClass) {
    myClass = aClass;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("move.class.to.separate.file.text", myClass.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.class.to.separate.file.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @NotNull PsiFile file) {
    if  (!myClass.isValid() || !myClass.getManager().isInProject(myClass)) return false;
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return false;
    try {
      JavaDirectoryServiceImpl.checkCreateClassOrInterface(dir, myClass.getName());
    }
    catch (IncorrectOperationException e) {
      return false;
    }

    return true;
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myClass;
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, @NotNull PsiFile file) {
    PsiDirectory dir = file.getContainingDirectory();
    String name = myClass.getName();
    JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
    PsiClass placeHolder = myClass.isInterface() ? directoryService.createInterface(dir, name) : directoryService.createClass(dir, name);
    WriteAction.run(() -> {
      PsiClass newClass = (PsiClass)placeHolder.replace(myClass);
      myClass.delete();

      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, newClass.getContainingFile().getVirtualFile(), newClass.getTextOffset());
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
