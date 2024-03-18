// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveClassToSeparateFileFix extends PsiUpdateModCommandAction<PsiClass> {
  public MoveClassToSeparateFileFix(@NotNull PsiClass aClass) {
    super(aClass);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.class.to.separate.file.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass aClass) {
    PsiDirectory dir = context.file().getContainingDirectory();
    if (dir == null) return null;
    try {
      JavaDirectoryServiceImpl.checkCreateClassOrInterface(dir, aClass.getName());
    }
    catch (IncorrectOperationException e) {
      return null;
    }

    return Presentation.of(QuickFixBundle.message("move.class.to.separate.file.text", aClass.getName()));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass myClass, @NotNull ModPsiUpdater updater) {
    PsiDirectory dir = updater.getWritable(context.file().getContainingDirectory());
    String name = myClass.getName();
    if (name == null) return;
    JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
    PsiClass placeHolder = myClass.isInterface() ? directoryService.createInterface(dir, name) : directoryService.createClass(dir, name);
    PsiClass newClass = (PsiClass)placeHolder.replace(myClass);
    myClass.delete();
    updater.moveCaretTo(newClass.getContainingFile());
  }

  @Override
  protected @NotNull IntentionPreviewInfo generatePreview(ActionContext context, PsiClass myClass) {
    return IntentionPreviewInfo.movePsi(myClass, myClass.getContainingFile().getContainingDirectory());
  }
}
