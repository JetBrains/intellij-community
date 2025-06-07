// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.wrappers;

import com.intellij.pom.PomModelAspect;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;

public final class PsiEventWrapperAspect implements PomModelAspect {
  private final @NotNull TreeAspect myTreeAspect;

  public PsiEventWrapperAspect(@NotNull TreeAspect treeAspect) {
    myTreeAspect = treeAspect;
  }

  @Override
  public void update(@NotNull PomModelEvent event) {
    TreeChangeEventImpl changeSet = (TreeChangeEventImpl)event.getChangeSet(myTreeAspect);
    if(changeSet == null) return;

    PsiFile file = (PsiFile)changeSet.getRootElement().getPsi();

    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject())).getSynchronizer().processEvents(changeSet, file);

    if (PomModelImpl.shouldFirePhysicalPsiEvents(file)) {
      changeSet.fireEvents();
    }
    else {
      ((PsiManagerEx)file.getManager()).afterChange(false);
    }
  }

}
