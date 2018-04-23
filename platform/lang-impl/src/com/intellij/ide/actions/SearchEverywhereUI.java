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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 * @author Mikhail.Sokolov
 */
public class SearchEverywhereUI extends BorderLayoutPanel {
  private SETab mySelectedTab;
  private final JTextField mySearchField;
  private final JCheckBox myNonProjectCB;
  private final List<SETab> myTabs = new ArrayList<>();

  public SearchEverywhereUI(List<SearchEverywhereContributor> contributors, @Nullable SearchEverywhereContributor selected) {
    withMinimumWidth(670);
    withPreferredWidth(670);
    setBackground(JBUI.CurrentTheme.SearchEverywhere.dialogBackground());

    myNonProjectCB = new JBCheckBox();
    myNonProjectCB.setOpaque(false);
    myNonProjectCB.setFocusable(false);

    JPanel contributorsPanel = createTabPanel(contributors, selected);
    JPanel settingsPanel = createSettingsPanel();
    mySearchField = createSearchField();

    addToLeft(contributorsPanel);
    addToRight(settingsPanel);
    addToBottom(mySearchField);
  }

  public JTextField getSearchField() {
    return mySearchField;
  }

  public void setUseNonProjectItems(boolean use) {
    myNonProjectCB.setSelected(use);
  }

  public boolean isUseNonProjectItems() {
    return myNonProjectCB.isSelected();
  }

  private void switchToNextTab() {
    int currentIndex = myTabs.indexOf(mySelectedTab);
    SETab nextTab = currentIndex == myTabs.size() - 1 ? myTabs.get(0) : myTabs.get(currentIndex + 1);
    switchToTab(nextTab);
  }

  private void switchToTab(SETab tab) {
    mySelectedTab = tab;
    myNonProjectCB.setText(tab.getContributor().includeNonProjectItemsText());
    myNonProjectCB.setSelected(false);
    repaint();
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

    searchField.setFocusTraversalKeysEnabled(false);
    searchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_TAB && e.getModifiers() == 0) {
          switchToNextTab();
          e.consume();
        }
      }
    });

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
  private JPanel createTabPanel(List<SearchEverywhereContributor> contributors, @Nullable SearchEverywhereContributor selected) {
    JPanel contributorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    contributorsPanel.setOpaque(false);

    SETab allTab = new SETab(allElementsContributor);
    contributorsPanel.add(allTab);
    myTabs.add(allTab);


    contributors.forEach(contributor -> {
      SETab tab = new SETab(contributor);
      if (contributor == selected) {
        switchToTab(tab);
      }
      contributorsPanel.add(tab);
      myTabs.add(tab);
    });

    if (mySelectedTab == null) {
      switchToTab(allTab);
    }

    return contributorsPanel;
  }

  private final SearchEverywhereContributor allElementsContributor = new SearchEverywhereContributor() {
    @NotNull
    @Override
    public String getSearchProviderId() {
      return "All";
    }

    @NotNull
    @Override
    public String getGroupName() {
      return IdeBundle.message("searcheverywhere.allelements.tab.name");
    }

    @Override
    public int getSortWeight() {
      return 0;
    }
  };

  private class SETab extends JLabel {
    private final SearchEverywhereContributor myContributor;

    public SETab(SearchEverywhereContributor contributor) {
      super(contributor.getGroupName());
      myContributor = contributor;
      Insets insets = JBUI.CurrentTheme.SearchEverywhere.tabInsets();
      setBorder(JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right));
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          switchToTab(SETab.this);
        }
      });
    }

    public SearchEverywhereContributor getContributor() {
      return myContributor;
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
