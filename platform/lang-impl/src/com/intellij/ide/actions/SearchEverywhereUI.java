// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Bulenkov
 * @author Mikhail.Sokolov
 */
public class SearchEverywhereUI extends BorderLayoutPanel {
  private SETab mySelectedTab;
  private final JTextField mySearchField;
  private final JCheckBox myNonProjectCB = new JBCheckBox();

  public SearchEverywhereUI(@Nullable SearchEverywhereContributor selected) {
    withMinimumWidth(670);
    withPreferredWidth(670);
    setBackground(JBUI.CurrentTheme.SearchEverywhere.dialogBackground());

    JPanel contributorsPanel = createTabPanel(selected);
    JPanel settingsPanel = createSettingsPanel();
    mySearchField = createSearchField();

    addToLeft(contributorsPanel);
    addToRight(settingsPanel);
    addToBottom(mySearchField);
  }

  public JTextField getSearchField() {
    return mySearchField;
  }

  private JTextField createSearchField() {
    ExtendableTextField searchField = new ExtendableTextField() {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = JBUI.scale(29);
        return size;
      }
    };

    ExtendableTextField.Extension searchExtension = new ExtendableTextField.Extension() {
      @Override
      public Icon getIcon(boolean hovered) {
        return AllIcons.Actions.Search;
      }

      @Override
      public boolean isIconBeforeText() {
        return true;
      }
    };
    ExtendableTextField.Extension hintExtension = new ExtendableTextField.Extension() {
      private final TextIcon icon;
      {
        icon = new TextIcon(IdeBundle.message("searcheverywhere.switch.scope.hint"), JBColor.GRAY, null, 0);
        icon.setFont(RelativeFont.SMALL.derive(getFont()));
      }

      @Override
      public Icon getIcon(boolean hovered) {
        return icon;
      }
    };
    searchField.setExtensions(searchExtension, hintExtension);

    //todo gap between icon and text #UX-1
    Insets insets = JBUI.CurrentTheme.SearchEverywhere.searchFieldInsets();
    Border border = JBUI.Borders.merge(
      JBUI.Borders.empty(insets.top, searchExtension.getPreferredSpace() + insets.left, insets.bottom, hintExtension.getPreferredSpace() + insets.right),
      IdeBorderFactory.createBorder(JBUI.CurrentTheme.SearchEverywhere.searchFieldBorderColor(), SideBorder.BOTTOM | SideBorder.TOP),
      true);
    searchField.setBorder(border);
    searchField.setBackground(JBUI.CurrentTheme.SearchEverywhere.searchFieldBackground());

    return searchField;
  }

  private JPanel createSettingsPanel() {
    JPanel res = new JPanel();
    BoxLayout bl = new BoxLayout(res, BoxLayout.X_AXIS);
    res.setLayout(bl);
    res.setOpaque(false);

    res.add(myNonProjectCB);
    res.add(Box.createHorizontalStrut(JBUI.scale(19)));

    ToggleAction pinAction = new ToggleAction(null, null, AllIcons.General.AutohideOff) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return UISettings.getInstance().getPinFindInPath();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        UISettings.getInstance().setPinFindInPath(state);
      }
    };
    ActionButton pinButton = new ActionButton(pinAction, pinAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    res.add(pinButton);
    res.add(Box.createHorizontalStrut(JBUI.scale(10)));

    AnAction emptyAction = new AnAction(AllIcons.General.Filter) {
      @Override
      public void actionPerformed(AnActionEvent e) {}
    };
    ActionButton filterButton = new ActionButton(emptyAction, emptyAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    res.add(filterButton);
    res.add(Box.createHorizontalStrut(JBUI.scale(10)));

    return res;
  }

  @NotNull
  private JPanel createTabPanel(@Nullable SearchEverywhereContributor selected) {
    JPanel contributorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    contributorsPanel.setOpaque(false);

    String allNonProjectItemsText = IdeBundle.message("checkbox.include.non.project.items", IdeUICustomization.getInstance().getProjectConceptName());
    SETab allTab = new SETab(IdeBundle.message("searcheverywhere.allelements.tab.name"), allNonProjectItemsText);
    contributorsPanel.add(allTab);

    SearchEverywhereContributor.getProvidersSorted().forEach(contributor -> {
      SETab tab = new SETab(contributor);
      if (contributor == selected) {
        mySelectedTab = tab;
      }
      contributorsPanel.add(tab);
    });

    if (mySelectedTab == null) {
      mySelectedTab = allTab;
      myNonProjectCB.setText(allNonProjectItemsText);
    }

    return contributorsPanel;
  }

  private class SETab extends JLabel {
    public SETab(SearchEverywhereContributor contributor) {
      this(contributor.getGroupName(), contributor.includeNonProjectItemsText());
    }

    public SETab(String tabName, String nonProjectItemCBText) {
      super(tabName);
      Insets insets = JBUI.CurrentTheme.SearchEverywhere.tabInsets();
      setBorder(JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right));
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          mySelectedTab = SETab.this;
          SearchEverywhereUI.this.repaint();
          myNonProjectCB.setText(nonProjectItemCBText);
        }
      });
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = JBUI.scale(29);
      return size;
    }

    @Override
    public boolean isOpaque() {
      return mySelectedTab == this;
    }

    @Override
    public Color getBackground() {
      return mySelectedTab == this
             ? JBUI.CurrentTheme.SearchEverywhere.selectedTabColor()
             : super.getBackground();
    }
  }
}
