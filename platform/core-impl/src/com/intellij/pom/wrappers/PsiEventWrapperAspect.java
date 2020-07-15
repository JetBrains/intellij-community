// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.wrappers;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiToDocumentSynchronizer;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

public final class PsiEventWrapperAspect implements PomModelAspect {
  @NotNull
  private final TreeAspect myTreeAspect;

  public PsiEventWrapperAspect(@NotNull TreeAspect treeAspect) {
    myTreeAspect = treeAspect;
  }

  @Override
  public void update(@NotNull PomModelEvent event) {
    TreeChangeEventImpl changeSet = (TreeChangeEventImpl)event.getChangeSet(myTreeAspect);
    if(changeSet == null) return;

    ASTNode rootElement = changeSet.getRootElement();
    PsiFile file = (PsiFile)rootElement.getPsi();
    if (!file.isPhysical()) {
      promoteNonPhysicalChangesToDocument(rootElement, file);
      ((PsiManagerImpl)file.getManager()).afterChange(false);
      return;
    }

    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject())).getSynchronizer().processEvents(changeSet, file);
    changeSet.fireEvents();
  }

  private static void promoteNonPhysicalChangesToDocument(ASTNode rootElement, PsiFile file) {
    if (file instanceof DummyHolder) return;
    if (((PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject())).isCommitInProgress()) return;

    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    if (vFile instanceof LightVirtualFile && !(vFile instanceof VirtualFileWindow)) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(vFile);
      if (document != null) {
        CharSequence text = rootElement.getChars();
        PsiToDocumentSynchronizer.performAtomically(file, () -> document.replaceString(0, document.getTextLength(), text));
      }
    }
  }
}
