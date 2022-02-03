// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.actionSystem.CommonDataKeys.*;
import static com.intellij.openapi.actionSystem.LangDataKeys.PSI_ELEMENT_ARRAY;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;

public class CopyReferencePopup extends NonTrivialActionGroup {
  private static final Logger LOG = Logger.getInstance(CopyReferencePopup.class);
  private static final int DEFAULT_WIDTH = JBUIScale.scale(500);

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setPerformGroup(true);
    e.getPresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true);
    e.getPresentation().putClientProperty(ActionMenu.SUPPRESS_SUBMENU, true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = SimpleDataContext.builder()
      .addAll(e.getDataContext(), PSI_ELEMENT, PROJECT, PSI_ELEMENT_ARRAY, VIRTUAL_FILE_ARRAY, EDITOR)
      .build();
    String popupPlace = ActionPlaces.getActionGroupPopupPlace(getClass().getSimpleName());
    ListPopup popup = new PopupFactoryImpl.ActionGroupPopup(
      LangBundle.message("popup.title.copy"), this, e.getDataContext(), true, true, false, true, null, -1, null, popupPlace) {
      @Override
      protected ListCellRenderer<PopupFactoryImpl.ActionItem> getListElementRenderer() {
        return new PopupListElementRenderer<>(this) {
          private JLabel myInfoLabel;
          private JLabel myShortcutLabel;

          @Override
          protected void createLabel() {
            super.createLabel();

            myIconLabel.setBorder(JBUI.Borders.empty(1, 1, 1, JBUI.CurrentTheme.ActionsList.elementIconGap()));

            myInfoLabel = new JLabel();
            myInfoLabel.setBorder(JBUI.Borders.empty(1, DEFAULT_HGAP, 1, 1));

            myShortcutLabel = new JLabel();
            myShortcutLabel.setBorder(JBUI.Borders.emptyLeft(DEFAULT_HGAP));
            myShortcutLabel.setForeground(UIUtil.getContextHelpForeground());
          }

          @Override
          protected JComponent createItemComponent() {
            createLabel();

            JPanel panel = new JPanel(new GridBagLayout());

            GridBag gbc = new GridBag();

            panel.add(myIconLabel, gbc.next());
            panel.add(myTextLabel, gbc.next());
            panel.add(myShortcutLabel, gbc.next());
            panel.add(myInfoLabel, gbc.next().weightx(1));

            return layoutComponent(panel);
          }

          @Override
          protected void customizeComponent(@NotNull JList<? extends PopupFactoryImpl.ActionItem> list,
                                            @NotNull PopupFactoryImpl.ActionItem actionItem,
                                            boolean isSelected) {
            myNextStepButtonSeparator.setVisible(false);
            AnAction action = actionItem.getAction();
            Editor editor = EDITOR.getData(dataContext);
            java.util.List<PsiElement> elements = CopyReferenceUtil.getElementsToCopy(editor, dataContext);
            String qualifiedName = null;
            if (action instanceof CopyPathProvider) {
              qualifiedName = ((CopyPathProvider)action).getQualifiedName(getProject(), elements, editor, dataContext);
            }

            if (action instanceof CopyReferenceAction) {
              qualifiedName = ((CopyReferenceAction)action).getQualifiedName(editor, elements);
            }

            myInfoLabel.setText("");
            if (qualifiedName != null) {
              myInfoLabel.setText(qualifiedName);
            }
            Color foreground = isSelected ? UIUtil.getListSelectionForeground(true) : UIUtil.getInactiveTextColor();
            myInfoLabel.setForeground(foreground);
            myShortcutLabel.setForeground(foreground);

            MnemonicNavigationFilter<Object> filter = myStep.getMnemonicNavigationFilter();
            int pos = filter == null ? -1 : filter.getMnemonicPos(actionItem);
            if (pos != -1) {
              String text = myTextLabel.getText();
              text = text.substring(0, pos) + text.substring(pos + 1);
              myTextLabel.setText(text);
              myTextLabel.setDisplayedMnemonicIndex(pos);
            }

            if (action instanceof CopyPathProvider) {
              Shortcut shortcut = ArrayUtil.getFirstElement(action.getShortcutSet().getShortcuts());
              myShortcutLabel.setText(shortcut != null ? KeymapUtil.getShortcutText(shortcut) : null);
            }
          }
        };
      }

      @Override
      protected boolean isResizable() {
        return true;
      }
    };

    updatePopupSize(popup);

    popup.showInBestPositionFor(e.getDataContext());
  }

  @Nullable
  public ActionGroup getCopyReferenceActionGroup() {
    AnAction popupGroup = ActionManager.getInstance().getAction("CopyReferencePopupGroup");
    if (!(popupGroup instanceof DefaultActionGroup)) {
      LOG.warn("Cannot find 'CopyReferencePopup' action to show popup");
      return null;
    }
    return (ActionGroup)popupGroup;
  }

  private static void updatePopupSize(@NotNull ListPopup popup) {
    ApplicationManager.getApplication().invokeLater(() -> {
      popup.getContent().setPreferredSize(new Dimension(DEFAULT_WIDTH, popup.getContent().getPreferredSize().height));
      popup.getContent().setSize(new Dimension(DEFAULT_WIDTH, popup.getContent().getPreferredSize().height));
      popup.setSize(popup.getContent().getPreferredSize());
    });
  }
}