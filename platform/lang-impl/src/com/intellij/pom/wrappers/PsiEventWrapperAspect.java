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

package com.intellij.pom.wrappers;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.ReplaceChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.testFramework.LightVirtualFile;

import java.util.Collections;

public class PsiEventWrapperAspect implements PomModelAspect{
  private final TreeAspect myTreeAspect;

  public PsiEventWrapperAspect(PomModel model, TreeAspect aspect) {
    myTreeAspect = aspect;
    model.registerAspect(PsiEventWrapperAspect.class, this, Collections.singleton((PomModelAspect)aspect));
  }

  @Override
  public void update(PomModelEvent event) {
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myTreeAspect);
    if(changeSet == null) return;
    sendAfterEvents(changeSet);
  }

  private static void sendAfterEvents(TreeChangeEvent changeSet) {
    ASTNode rootElement = changeSet.getRootElement();
    PsiFile file = (PsiFile)rootElement.getPsi();
    PsiManagerImpl manager = (PsiManagerImpl)file.getManager();
    if(manager == null) return;

    if (!file.isPhysical()) {
      promoteNonPhysicalChangesToDocument(rootElement, file);
      manager.afterChange(false);
      return;
    }
    final ASTNode[] changedElements = changeSet.getChangedElements();
    for (ASTNode changedElement : changedElements) {
      TreeChange changesByElement = changeSet.getChangesByElement(changedElement);
      PsiElement psiParent = null;

      while (changedElement != null &&
             ((psiParent = changedElement.getPsi()) == null || !checkPsiForChildren(changesByElement.getAffectedChildren()))) {
        final ASTNode parent = changedElement.getTreeParent();
        final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, changedElement);
        changeInfo.compactChange(changesByElement);
        changesByElement = new TreeChangeImpl(parent);
        changesByElement.addChange(changedElement, changeInfo);
        changedElement = parent;
      }
      if (changedElement == null) continue;

      final ASTNode[] affectedChildren = changesByElement.getAffectedChildren();

      for (final ASTNode treeElement : affectedChildren) {
        PsiTreeChangeEventImpl psiEvent = new PsiTreeChangeEventImpl(manager);
        psiEvent.setParent(psiParent);
        psiEvent.setFile(file);

        final PsiElement psiChild = treeElement.getPsi();
        psiEvent.setChild(psiChild);

        final ChangeInfo changeByChild = changesByElement.getChangeByChild(treeElement);
        switch (changeByChild.getChangeType()) {
          case ChangeInfo.ADD:
            psiEvent.setOffset(treeElement.getStartOffset());
            psiEvent.setOldLength(0);
            manager.childAdded(psiEvent);
            break;
          case ChangeInfo.REPLACE:
            final ReplaceChangeInfo change = (ReplaceChangeInfo)changeByChild;
            psiEvent.setOffset(treeElement.getStartOffset());
            final ASTNode replaced = change.getReplaced();
            psiEvent.setOldChild(replaced.getPsi());
            psiEvent.setNewChild(psiChild);
            psiEvent.setOldLength(replaced.getTextLength());
            manager.childReplaced(psiEvent);
            break;
          case ChangeInfo.CONTENTS_CHANGED:
            psiEvent.setOffset(treeElement.getStartOffset());
            psiEvent.setParent(psiChild);
            psiEvent.setOldLength(changeByChild.getOldLength());
            manager.childrenChanged(psiEvent);
            break;
          case ChangeInfo.REMOVED:
            psiEvent.setOffset(changesByElement.getChildOffsetInNewTree(treeElement));
            psiEvent.setOldParent(psiParent);
            psiEvent.setOldChild(psiChild);
            psiEvent.setOldLength(changeByChild.getOldLength());
            manager.childRemoved(psiEvent);
            break;
        }
      }
    }
  }

  private static void promoteNonPhysicalChangesToDocument(ASTNode rootElement, PsiFile file) {
    if (file instanceof DummyHolder) return;
    if (((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(file.getProject())).isCommitInProgress()) return;
    
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    if (vFile instanceof LightVirtualFile && !(vFile instanceof VirtualFileWindow)) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(vFile);
      if (document != null) {
        CharSequence text = rootElement.getChars();
        PsiToDocumentSynchronizer.performAtomically(file, () -> document.replaceString(0, document.getTextLength(), text));
      }
    }
  }

  private static boolean checkPsiForChildren(final ASTNode[] affectedChildren) {
    for (final ASTNode astNode : affectedChildren) {
      //if (TreeUtil.isCollapsedChameleon(astNode)) return false;
      if (astNode.getPsi() == null) return false;
    }
    return true;
  }
}
