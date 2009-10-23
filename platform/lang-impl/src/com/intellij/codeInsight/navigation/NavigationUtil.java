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

package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * @author ven
 */
public final class NavigationUtil {

  private NavigationUtil() {
  }

  public static JBPopup getPsiElementPopup(PsiElement[] elements, String title) {
    return getPsiElementPopup(elements, new DefaultPsiElementCellRenderer(), title);
  }

  public static JBPopup getPsiElementPopup(final PsiElement[] elements, final PsiElementListCellRenderer renderer, final String title) {
    return getPsiElementPopup(elements, renderer, title, new PsiElementProcessor<PsiElement>() {
      public boolean execute(final PsiElement element) {
        Navigatable descriptor = EditSourceUtil.getDescriptor(element);
        if (descriptor != null && descriptor.canNavigate()) {
          descriptor.navigate(true);
        }
        return true;
      }
    });
  }

  public static <T extends PsiElement> JBPopup getPsiElementPopup(final T[] elements, final PsiElementListCellRenderer<T> renderer,
                                                                  final String title, final PsiElementProcessor<T> processor) {
    final JList list = new JList(elements);
    list.setCellRenderer(renderer);

    final Runnable runnable = new Runnable() {
      public void run() {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        for (Object element : list.getSelectedValues()) {
          if (element != null) {
            processor.execute((T)element);
          }
        }
      }
    };

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }
    renderer.installSpeedSearch(builder);

    return builder.setItemChoosenCallback(runnable).createPopup();
  }

  public static void showPsiElementPopup(PsiElement[] elements, String title, MouseEvent event) {
    getPsiElementPopup(elements, title).show(new RelativePoint(event));
  }
}
