// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.util.PsiTreeUtil;
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
    if  (!myClass.isValid() || !BaseIntentionAction.canModify(myClass)) return false;
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

      Navigatable descriptor = PsiNavigationSupport.getInstance().createNavigatable(project,
                                                                                    newClass.getContainingFile()
                                                                                            .getVirtualFile(),
                                                                                    newClass.getTextOffset());
      descriptor.navigate(true);
    });
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiClass classInCopy = PsiTreeUtil.findSameElementInCopy(myClass, file);
    return IntentionPreviewInfo.movePsi(classInCopy, myClass.getContainingFile().getContainingDirectory());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
