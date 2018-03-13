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
    PsiFile file = descriptor.getPsiElement().getContainingFile();
    setOption(file, myValue);
    final VirtualFile vFile = file.getVirtualFile();
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
      @Override
      public void undo() {
        setOption(file, !myValue);
      }

      @Override
      public void redo() {
        setOption(file, myValue);
      }
    });
  }

  private void setOption(@NotNull PsiFile file, boolean value) {
    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(file.getProject(), model -> {
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
