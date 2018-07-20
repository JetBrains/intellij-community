// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SetInspectionOptionFix implements LocalQuickFix, LowPriorityAction, Iconable {
  private final String myID;
  private final String myProperty;
  private final String myMessage;
  private final boolean myValue;

  public SetInspectionOptionFix(LocalInspectionTool inspection, String property, String message, boolean value) {
    myID = inspection.getID();
    myProperty = property;
    myMessage = message;
    myValue = value;
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return myMessage;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Set inspection option";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    VirtualFile vFile = descriptor.getPsiElement().getContainingFile().getVirtualFile();
    setOption(project, vFile, myValue);
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
      @Override
      public void undo() {
        setOption(project, vFile, !myValue);
      }

      @Override
      public void redo() {
        setOption(project, vFile, myValue);
      }
    });
  }

  private void setOption(@NotNull Project project, @NotNull VirtualFile vFile, boolean value) {
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    if (file == null) return;
    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, model -> {
      InspectionToolWrapper tool = model.getToolById(myID, file);
      if(tool == null) return;
      InspectionProfileEntry inspection = tool.getTool();
      ReflectionUtil.setField(inspection.getClass(), inspection, boolean.class, myProperty, value);
    });
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }
}
