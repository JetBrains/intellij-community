// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.ui.popup.ActionPopupOptions;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;

public class CopyReferencePopup extends NonTrivialActionGroup {
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
    PresentationFactory factory = new PresentationFactory();
    ActionPopupOptions options = ActionPopupOptions.create(true, true, false, true, -1, false, null);
    ListPopup popup = new PopupFactoryImpl.ActionGroupPopup(
      null, LangBundle.message("popup.title.copy"), this, e.getDataContext(),
      ActionPlaces.COPY_REFERENCE_POPUP, factory, options, null) {
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
            myButtonSeparator.setVisible(false);
            AnAction action = actionItem.getAction();
            Presentation presentation = factory.getPresentation(action);
            String qualifiedName = presentation.getClientProperty(CopyPathProvider.QUALIFIED_NAME);
            myInfoLabel.setText("");
            if (qualifiedName != null) {
              myInfoLabel.setText(qualifiedName);
            }
            Color foreground = isSelected ? NamedColorUtil.getListSelectionForeground(true) : NamedColorUtil.getInactiveTextColor();
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
      @Override
      public @NotNull Dimension getPreferredContentSize() {
        return new Dimension(DEFAULT_WIDTH, super.getPreferredContentSize().height);
      }
      @Override
      public Dimension getSize() {
        return getPreferredContentSize();
      }
    };

    updatePopupSize(popup);

    popup.showInBestPositionFor(e.getDataContext());
  }

  private static void updatePopupSize(@NotNull ListPopup popup) {
    ApplicationManager.getApplication().invokeLater(() -> {
      popup.getContent().setPreferredSize(new Dimension(DEFAULT_WIDTH, popup.getContent().getPreferredSize().height));
      popup.getContent().setSize(new Dimension(DEFAULT_WIDTH, popup.getContent().getPreferredSize().height));
      popup.setSize(popup.getContent().getPreferredSize());
    });
  }
}