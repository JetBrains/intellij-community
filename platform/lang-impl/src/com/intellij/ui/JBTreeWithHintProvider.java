/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBTreeWithHintProvider extends Tree {
  private JBPopup myHint;
  
  public JBTreeWithHintProvider() {
    addSelectionListener();
  }

  public JBTreeWithHintProvider(TreeModel treemodel) {
    super(treemodel);
    addSelectionListener();
  }

  public JBTreeWithHintProvider(TreeNode root) {
    super(root);
    addSelectionListener();
  }

  private void addSelectionListener() {
    addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        if (getClientProperty(PopupChooserBuilder.SELECTED_BY_MOUSE_EVENT) != Boolean.TRUE) {
          final TreePath path = getSelectionPath();
          if (path != null) {
            final PsiElement psiElement = getPsiElementForHint(path.getLastPathComponent());
            if (psiElement != null && psiElement.isValid()) {
              updateHint(psiElement);
            }
          }
        }
      }
    });
  }
  
  protected abstract PsiElement getPsiElementForHint(final Object selectedValue);

  public void registerHint(final JBPopup hint) {
    hideHint();
    myHint = hint;
  }

  public void hideHint() {
    if (myHint != null && myHint.isVisible()) {
      myHint.cancel();
    }

    myHint = null;
  }

  public void updateHint(PsiElement element) {
    if (myHint == null || !myHint.isVisible()) return;

    final PopupUpdateProcessor updateProcessor = myHint.getUserData(PopupUpdateProcessor.class);
    if (updateProcessor != null) {
      updateProcessor.updatePopup(element);
    }
  }
}
