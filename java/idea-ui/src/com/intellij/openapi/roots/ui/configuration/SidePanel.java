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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SidePanel extends JPanel {

  private final JList myList;
  private final DefaultListModel myModel;
  private final Place.Navigator myNavigator;
  private final ArrayList<Place> myPlaces = new ArrayList<Place>();

  private final Map<Integer, String> myIndex2Separator = new HashMap<Integer, String>();
  private final Map<Place, Presentation> myPlace2Presentation = new HashMap<Place, Presentation>();
  private final History myHistory;

  public SidePanel(Place.Navigator navigator, History history) {
    myHistory = history;
    myNavigator = navigator;

    setLayout(new BorderLayout());

    myModel = new DefaultListModel();
    myList = new JBList(myModel);

    final ListItemDescriptor descriptor = new ListItemDescriptor() {
      public String getTextFor(final Object value) {
        return myPlace2Presentation.get(value).getText();
      }

      public String getTooltipFor(final Object value) {
        return getTextFor(value);
      }

      public Icon getIconFor(final Object value) {
        return null;
        //return myPlace2Presentation.get(value).getIcon();
      }

      public boolean hasSeparatorAboveOf(final Object value) {
        final int index = myPlaces.indexOf(value);
        return myIndex2Separator.get(index) != null;
      }

      public String getCaptionAboveOf(final Object value) {
        return myIndex2Separator.get(myPlaces.indexOf(value));
      }
    };

    myList.setCellRenderer(new GroupedItemsListRenderer(descriptor));


    add(new JScrollPane(myList), BorderLayout.CENTER);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        final Object value = myList.getSelectedValue();
        if (value != null) {
          myNavigator.navigateTo(((Place)value), false);
        }
      }
    });
  }

  public void addPlace(Place place, @NotNull Presentation presentation) {
    myModel.addElement(place);
    myPlaces.add(place);
    myPlace2Presentation.put(place, presentation);
    revalidate();
    repaint();
  }

  public void addSeparator(String text) {
    myIndex2Separator.put(myPlaces.size(), text);
  }

  public Collection<Place> getPlaces() {
    return myPlaces;
  }

  public void select(final Place place) {
    myList.setSelectedValue(place, true);
  }
}
