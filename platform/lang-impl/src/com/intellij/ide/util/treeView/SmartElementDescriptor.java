// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SmartElementDescriptor extends NodeDescriptor{
  private final SmartPsiElementPointer mySmartPointer;

  public SmartElementDescriptor(@NotNull Project project,
                                @Nullable NodeDescriptor parentDescriptor,
                                @NotNull PsiElement element) {
    super(project, parentDescriptor);
    mySmartPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
  }

  public final @Nullable PsiElement getPsiElement() {
    return mySmartPointer.getElement();
  }

  @Override
  public Object getElement() {
    return getPsiElement();
  }

  protected boolean isMarkReadOnly() {
    return getParentDescriptor() instanceof PsiDirectoryNode;
  }

  protected boolean isMarkModified() {
    return getParentDescriptor() instanceof PsiDirectoryNode;
  }

  // Should be called in atomic action
  @Override
  public boolean update() {
    PsiElement element = mySmartPointer.getElement();
    if (element == null) return true;

    Icon icon = getIcon(element);
    Color color = null;

    if (isMarkModified() ){
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
      if (virtualFile != null) {
        color = FileStatusManager.getInstance(myProject).getStatus(virtualFile).getColor();
      }
    }
    if (CopyPasteManager.getInstance().isCutElement(element)) {
      color = CopyPasteManager.getCutColor();
    }

    boolean changes = !Comparing.equal(icon, getIcon()) || !Comparing.equal(color, myColor);
    setIcon(icon);
    myColor = color;
    return changes;
  }

  protected @Nullable Icon getIcon(@NotNull PsiElement element) {
    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    try {
      return element.getIcon(flags);
    }
    catch (IndexNotReadyException ignored) {
      return null;
    }
  }
}
