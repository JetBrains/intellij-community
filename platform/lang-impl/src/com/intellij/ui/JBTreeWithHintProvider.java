/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.PopupUpdateProcessor;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * @author Konstantin Bulenkov
 */
public class JBTreeWithHintProvider extends DnDAwareTree {
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
        if (isHintBeingShown() && getClientProperty(ListUtil.SELECTED_BY_MOUSE_EVENT) != Boolean.TRUE) {
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

  @Nullable
  protected PsiElement getPsiElementForHint(final Object selectedValue) {
    // default implementation
    return CommonDataKeys.PSI_ELEMENT.getData(DataManager.getInstance().getDataContext(this));
  }

  public void registerHint(final JBPopup hint) {
    hideHint();
    myHint = hint;
  }

  public void hideHint() {
    if (isHintBeingShown()) {
      myHint.cancel();
    }

    myHint = null;
  }

  public void updateHint(PsiElement element) {
    if (!isHintBeingShown()) return;

    final PopupUpdateProcessor updateProcessor = myHint.getUserData(PopupUpdateProcessor.class);
    if (updateProcessor != null) {
      updateProcessor.updatePopup(element);
    }
  }

  private boolean isHintBeingShown() {
    return myHint != null && myHint.isVisible();
  }
}
