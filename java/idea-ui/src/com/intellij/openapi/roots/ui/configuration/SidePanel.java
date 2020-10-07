// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

public final class SidePanel extends JPanel {
  private final JList<SidePanelItem> myList;
  private final DefaultListModel<SidePanelItem> myModel;
  private final Place.Navigator myNavigator;

  private final Int2ObjectMap<@Nls String> myIndex2Separator = new Int2ObjectOpenHashMap<>();

  public SidePanel(Place.Navigator navigator) {
    myNavigator = navigator;

    setLayout(new BorderLayout());

    myModel = new DefaultListModel<>();
    myList = new JBList<>(myModel);
    myList.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    myList.setBorder(JBUI.Borders.emptyTop(5));
    final ListItemDescriptor<SidePanelItem> descriptor = new ListItemDescriptor<>() {
      @Override
      public String getTextFor(final SidePanelItem value) {
        return value.myText;
      }

      @Override
      public String getTooltipFor(final SidePanelItem value) {
        return null;
      }

      @Override
      public Icon getIconFor(final SidePanelItem value) {
        return JBUIScale.scaleIcon(EmptyIcon.create(16, 20));
      }

      @Override
      public boolean hasSeparatorAboveOf(final SidePanelItem value) {
        return getSeparatorAbove(value) != null;
      }

      @Override
      public String getCaptionAboveOf(final SidePanelItem value) {
        return getSeparatorAbove(value);
      }
    };

    myList.setCellRenderer(new GroupedItemsListRenderer<>(descriptor) {
      JPanel myExtraPanel;
      SidePanelCountLabel myCountLabel;
      final CellRendererPane myValidationParent = new CellRendererPane();
      {
        mySeparatorComponent.setCaptionCentered(false);
        myList.add(myValidationParent);
      }

      @Override
      protected SeparatorWithText createSeparator() {
        return new SidePanelSeparator();
      }

      @Override
      protected void layout() {
        myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
        myExtraPanel.add(myComponent, BorderLayout.CENTER);
        myExtraPanel.add(myCountLabel, BorderLayout.EAST);
        myRendererComponent.add(myExtraPanel, BorderLayout.CENTER);
      }

      @Override
      public Component getListCellRendererComponent(JList<? extends SidePanelItem> list,
                                                    SidePanelItem value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        layout();
        myCountLabel.setText("");
        final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if ("Problems".equals(descriptor.getTextFor(value))) {
          final ErrorPaneConfigurable errorPane = (ErrorPaneConfigurable)value.myPlace.getPath("category");
          int errorsCount;
          if (errorPane != null && (errorsCount = errorPane.getErrorsCount()) > 0) {
            myCountLabel.setSelected(isSelected);
            myCountLabel.setText(errorsCount > 100 ? "100+" : String.valueOf(errorsCount));
          }
        }
        if (UIUtil.isClientPropertyTrue(list, ExpandableItemsHandler.EXPANDED_RENDERER)) {
          Rectangle bounds = list.getCellBounds(index, index);
          bounds.setSize((int)component.getPreferredSize().getWidth(), (int)bounds.getHeight());
          AbstractExpandableItemsHandler.setRelativeBounds(component, bounds, myExtraPanel, myValidationParent);
          myExtraPanel.setSize((int)myExtraPanel.getPreferredSize().getWidth(), myExtraPanel.getHeight());
          ComponentUtil.putClientProperty(myExtraPanel, ExpandableItemsHandler.USE_RENDERER_BOUNDS, true);
          return myExtraPanel;
        }
        return component;
      }

      @Override
      protected JComponent createItemComponent() {
        myExtraPanel = new NonOpaquePanel(new BorderLayout());
        myCountLabel = new SidePanelCountLabel();
        final JComponent component = super.createItemComponent();

        myTextLabel.setForeground(Gray._240);
        myTextLabel.setOpaque(true);

        return component;
      }

      @Override
      protected Color getBackground() {
        return UIUtil.SIDE_PANEL_BACKGROUND;
      }
    });

    add(ScrollPaneFactory.createScrollPane(myList, true), BorderLayout.CENTER);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        final SidePanelItem value = myList.getSelectedValue();
        if (value != null) {
          myNavigator.navigateTo(value.myPlace, false);
        }
      }
    });
  }

  public JList getList() {
    return myList;
  }

  public void addPlace(Place place, @NotNull Presentation presentation) {
    myModel.addElement(new SidePanelItem(place, presentation.getText()));
    revalidate();
    repaint();
  }

  public void addSeparator(@Nls String text) {
    myIndex2Separator.put(myModel.size(), text);
  }

  @Nullable
  private @NlsContexts.Separator String getSeparatorAbove(final SidePanelItem item) {
    return myIndex2Separator.get(myModel.indexOf(item));
  }

  public void select(final Place place) {
    for (int i = 0; i < myModel.getSize(); i++) {
      SidePanelItem item = myModel.getElementAt(i);
      if (place.equals(item.myPlace)) {
        myList.setSelectedValue(item, true);
      }
    }
  }

  private static class SidePanelItem {
    private final Place myPlace;
    private final @Nls String myText;

    SidePanelItem(Place place, @Nls String text) {
      myPlace = place;
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }
  }

}
