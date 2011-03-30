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

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

    List<GotoRelatedItem> items = getItems(psiFile, editor);
    if (items.isEmpty()) return;
    if (items.size() == 1) {
      items.get(0).navigate();
      return;
    }

    final JBList list = new JBList(new CollectionListModel(items));
    list.setCellRenderer(new ItemCellRenderer());

    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Goto Related")
      .setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          Object value = list.getSelectedValue();
          if (value instanceof GotoRelatedItem) {
            ((GotoRelatedItem)value).navigate();
          }
        }
      })
      .createPopup()
      .showInBestPositionFor(context);
  }

  @NotNull
  public static List<GotoRelatedItem> getItems(@NotNull PsiFile psiFile, @Nullable Editor editor) {
    PsiElement context = psiFile;
    if (editor != null) {
      PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        context = element;
      }
    }

    List<GotoRelatedItem> items = new ArrayList<GotoRelatedItem>();

    for (GotoRelatedProvider provider : Extensions.getExtensions(GotoRelatedProvider.EP_NAME)) {
      items.addAll(provider.getItems(context));
    }
    return items;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(LangDataKeys.PSI_FILE.getData(e.getDataContext()) != null);
  }

  private static class ItemCellRenderer extends JPanel implements ListCellRenderer {

    private final JLabel myLeft = new JLabel();
    private final JLabel myRight = new JLabel();
    private final JPanel mySpacer = new JPanel();

    private ItemCellRenderer() {
      super(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
      add(myLeft, BorderLayout.WEST);
      add(myRight, BorderLayout.EAST);

      mySpacer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
      mySpacer.setOpaque(false);
      add(mySpacer, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      GotoRelatedItem item = (GotoRelatedItem)value;
      myLeft.setText(item.getText());
      myLeft.setIcon(item.getIcon());

      PsiFile file = item.getContainingFile();
      myRight.setText(file == null ? null : file.getName());
      myRight.setIcon(file == null ? null : file.getIcon(0));

      setBackground(UIUtil.getListBackground(isSelected));
      Color foreground = UIUtil.getListForeground(isSelected);
      myLeft.setForeground(foreground);
      myRight.setForeground(foreground);
      return this;
    }
  }
}
