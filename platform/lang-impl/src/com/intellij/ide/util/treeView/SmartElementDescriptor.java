/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class SmartElementDescriptor extends NodeDescriptor{
  protected PsiElement myElement;
  private final SmartPsiElementPointer mySmartPointer;

  public SmartElementDescriptor(@NotNull Project project, NodeDescriptor parentDescriptor, @NotNull PsiElement element) {
    super(project, parentDescriptor);
    myElement = element;
    mySmartPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
  }

  @Override
  public Object getElement() {
    return myElement;
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
    myElement = mySmartPointer.getElement();
    if (myElement == null) return true;
    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()){
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }
    Icon icon = null;
    try {
      icon = myElement.getIcon(flags);
    }
    catch (IndexNotReadyException e) {
    }
    Color color = null;

    if (isMarkModified() ){
      VirtualFile virtualFile = PsiUtilBase.getVirtualFile(myElement);
      if (virtualFile != null) {
        color = FileStatusManager.getInstance(myProject).getStatus(virtualFile).getColor();
      }
    }
    if (CopyPasteManager.getInstance().isCutElement(myElement)) {
      color = CopyPasteManager.CUT_COLOR;
    }

    boolean changes = !Comparing.equal(icon, getIcon()) || !Comparing.equal(color, myColor);
    setIcon(icon);
    myColor = color;
    return changes;
  }
}
