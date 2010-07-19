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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class PsiElementListNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PsiElementListNavigator");

  private PsiElementListNavigator() {
  }

  public static void openTargets(MouseEvent e, NavigatablePsiElement[] targets, String title, ListCellRenderer listRenderer) {
    if (targets.length == 0) return;
    if (targets.length == 1){
      targets[0].navigate(true);
    }
    else{
      final JList list = new JBList(targets);
      list.setCellRenderer(listRenderer);

      final PopupChooserBuilder builder = new PopupChooserBuilder(list);
      if (listRenderer instanceof PsiElementListCellRenderer) {
        ((PsiElementListCellRenderer)listRenderer).installSpeedSearch(builder);
      }

      builder.
        setTitle(title).
        setMovable(true).
        setItemChoosenCallback(new Runnable() {
          public void run() {
            int[] ids = list.getSelectedIndices();
            if (ids == null || ids.length == 0) return;
            Object [] selectedElements = list.getSelectedValues();
            for (Object element : selectedElements) {
              PsiElement selected = (PsiElement) element;
              LOG.assertTrue(selected.isValid());
              ((NavigatablePsiElement)selected).navigate(true);
            }
          }
        }).createPopup().
        show(new RelativePoint(e));
    }
  }
}
