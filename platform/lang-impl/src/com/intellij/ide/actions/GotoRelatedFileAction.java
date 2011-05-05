/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class GotoRelatedFileAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {

    DataContext context = e.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(context);
    PsiFile psiFile = LangDataKeys.PSI_FILE.getData(context);
    if (psiFile == null) return;

    List<GotoRelatedItem> items = getItems(psiFile, editor, context);
    if (items.isEmpty()) return;
    if (items.size() == 1 && items.get(0).getElement() != null) {
      items.get(0).navigate();
      return;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      System.out.println(items);
    }
    createPopup(items, "Goto Related").showInBestPositionFor(context);
  }

  public static JBPopup createPopup(final List<? extends GotoRelatedItem> items, final String title) {
    Object[] elements = new Object[items.size()];
    //todo[nik] move presentation logic to GotoRelatedItem class
    final Map<PsiElement, GotoRelatedItem> itemsMap = new HashMap<PsiElement, GotoRelatedItem>();
    for (int i = 0; i < items.size(); i++) {
      GotoRelatedItem item = items.get(i);
      elements[i] = item.getElement() != null ? item.getElement() : item;
      itemsMap.put(item.getElement(), item);
    }

    return getPsiElementPopup(elements, new DefaultPsiElementCellRenderer() {
                                @Override
                                public String getElementText(PsiElement element) {
                                  String customName = itemsMap.get(element).getCustomName();
                                  return customName != null ? customName : super.getElementText(element);
                                }

                                @Override
                                protected Icon getIcon(PsiElement element) {
                                  Icon customIcon = itemsMap.get(element).getCustomIcon();
                                  return customIcon != null ? customIcon : super.getIcon(element);
                                }

                                @Override
                                public String getContainerText(PsiElement element, String name) {
                                  PsiFile file = element.getContainingFile();
                                  return file != null && !getElementText(element).equals(file.getName()) ? "(" + file.getName() + ")" : null;
                                }

                                @Override
                                protected DefaultListCellRenderer getRightCellRenderer() {
                                  return null;
                                }

                                @Override
                                protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer,
                                                                                     JList list,
                                                                                     Object value,
                                                                                     int index,
                                                                                     boolean selected,
                                                                                     boolean hasFocus) {
                                  final GotoRelatedItem item = (GotoRelatedItem)value;
                                  Color color = list.getForeground();
                                  final SimpleTextAttributes nameAttributes = new SimpleTextAttributes(Font.PLAIN, color);
                                  final String name = item.getCustomName();
                                  if (name == null) return false;
                                  renderer.append(name, nameAttributes);
                                  renderer.setIcon(item.getCustomIcon());
                                  return true;
                                }
                              }, title, new Processor<Object>() {
      @Override
      public boolean process(Object element) {
        if (element instanceof PsiElement) {
          //noinspection SuspiciousMethodCalls
          itemsMap.get(element).navigate();
        } else {
          ((GotoRelatedItem)element).navigate();
        }
        return true;
      }
    }
    );
  }

  public static JBPopup getPsiElementPopup(final Object[] elements, final PsiElementListCellRenderer<PsiElement> renderer,
                                                                    final String title, final Processor<Object> processor) {
    final JList list = new JBList(elements);
    list.setCellRenderer(renderer);

    final Runnable runnable = new Runnable() {
      public void run() {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        for (Object element : list.getSelectedValues()) {
          if (element != null) {
            processor.process(element);
          }
        }
      }
    };

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }
    renderer.installSpeedSearch(builder, true);

    return builder.setItemChoosenCallback(runnable).createPopup();
  }

  @NotNull
  public static List<GotoRelatedItem> getItems(@NotNull PsiFile psiFile, @Nullable Editor editor, @Nullable DataContext dataContext) {
    PsiElement contextElement = psiFile;
    if (editor != null) {
      PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        contextElement = element;
      }
    }

    List<GotoRelatedItem> items = new ArrayList<GotoRelatedItem>();

    for (GotoRelatedProvider provider : Extensions.getExtensions(GotoRelatedProvider.EP_NAME)) {
      items.addAll(provider.getItems(contextElement));
      if (dataContext != null) {
        items.addAll(provider.getItems(dataContext));
      }
    }
    return items;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(LangDataKeys.PSI_FILE.getData(e.getDataContext()) != null);
  }
}
