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

import com.intellij.codeInsight.navigation.ListBackgroundUpdaterTask;
import com.intellij.find.FindUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.JBListWithHintProvider;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class PsiElementListNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PsiElementListNavigator");

  private PsiElementListNavigator() {
  }

  public static void openTargets(MouseEvent e, NavigatablePsiElement[] targets, String title, final String findUsagesTitle, ListCellRenderer listRenderer) {
    openTargets(e, targets, title, findUsagesTitle, listRenderer, null);
  }

  public static void openTargets(MouseEvent e,
                                 NavigatablePsiElement[] targets,
                                 String title,
                                 final String findUsagesTitle,
                                 ListCellRenderer listRenderer,
                                 @Nullable ListBackgroundUpdaterTask listUpdaterTask) {
    JBPopup popup = navigateOrCreatePopup(targets, title, findUsagesTitle, listRenderer, listUpdaterTask);
    if (popup != null) popup.show(new RelativePoint(e));
  }

  public static void openTargets(Editor e, NavigatablePsiElement[] targets, String title, final String findUsagesTitle, ListCellRenderer listRenderer) {
    JBPopup popup = navigateOrCreatePopup(targets, title, findUsagesTitle, listRenderer, null);
    if (popup != null) popup.showInBestPositionFor(e);
  }

  @Nullable
  private static JBPopup navigateOrCreatePopup(final NavigatablePsiElement[] targets,
                                               final String title,
                                               final String findUsagesTitle,
                                               final ListCellRenderer listRenderer,
                                               final @Nullable ListBackgroundUpdaterTask listUpdaterTask) {
    if (targets.length == 0) return null;
    if (targets.length == 1) {
      targets[0].navigate(true);
      return null;
    }
    final CollectionListModel<NavigatablePsiElement> model = new CollectionListModel<NavigatablePsiElement>(targets);
    final JBListWithHintProvider list = new JBListWithHintProvider(model) {
      @Override
      protected PsiElement getPsiElementForHint(final Object selectedValue) {
        return (PsiElement) selectedValue;
      }
    };

    list.setCellRenderer(listRenderer);

    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (listRenderer instanceof PsiElementListCellRenderer) {
      ((PsiElementListCellRenderer)listRenderer).installSpeedSearch(builder);
    }

    PopupChooserBuilder popupChooserBuilder = builder.
      setTitle(title).
      setMovable(true).
      setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          int[] ids = list.getSelectedIndices();
          if (ids == null || ids.length == 0) return;
          Object[] selectedElements = list.getSelectedValues();
          for (Object element : selectedElements) {
            PsiElement selected = (PsiElement)element;
            LOG.assertTrue(selected.isValid());
            ((NavigatablePsiElement)selected).navigate(true);
          }
        }
      }).
      setCancelCallback(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          list.hideHint();

          return true;
        }
      });
    if (findUsagesTitle != null) {
      popupChooserBuilder = popupChooserBuilder.setCouldPin(new Processor<JBPopup>() {
        @Override
        public boolean process(JBPopup popup) {
          final List<NavigatablePsiElement> items = model.getItems();
          FindUtil.showInUsageView(null, items.toArray(new PsiElement[items.size()]), findUsagesTitle, targets[0].getProject());
          popup.cancel();
          return false;
        }
      });
    }

    final JBPopup popup = popupChooserBuilder.createPopup();
    if (listUpdaterTask != null) {
      listUpdaterTask.init((AbstractPopup)popup, list);

      ProgressManager.getInstance().run(listUpdaterTask);
    }
    return popup;
  }
}
