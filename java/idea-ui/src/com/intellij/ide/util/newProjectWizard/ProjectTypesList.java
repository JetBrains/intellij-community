/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.ProjectTemplate;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 11/21/12
 */
public class ProjectTypesList  {

  private final JBList myList;
  private final SearchTextField mySearchField;
  private final FilteringListModel<TemplateItem> myFilteringListModel;
  private MinusculeMatcher myMatcher;
  private Pair<TemplateItem, Integer> myBestMatch;

  public ProjectTypesList(JBList list, SearchTextField searchField, MultiMap<TemplatesGroup, ProjectTemplate> map) {
    myList = list;
    mySearchField = searchField;

    CollectionListModel<TemplateItem> model = new CollectionListModel<TemplateItem>(buildItems(map));
    myFilteringListModel = new FilteringListModel<TemplateItem>(model);

    myList.setCellRenderer(new GroupedItemsListRenderer(new ListItemDescriptor() {
      @Nullable
      @Override
      public String getTextFor(Object value) {
        return ((TemplateItem)value).getName();
      }

      @Nullable
      @Override
      public String getTooltipFor(Object value) {
        return null;
      }

      @Nullable
      @Override
      public Icon getIconFor(Object value) {
        return ((TemplateItem)value).getIcon();
      }

      @Override
      public boolean hasSeparatorAboveOf(Object value) {
        TemplateItem item = (TemplateItem)value;
        int index = myFilteringListModel.getElementIndex(item);
        return index == 0 || !myFilteringListModel.getElementAt(index -1).getGroupName().equals(item.getGroupName());
      }

      @Nullable
      @Override
      public String getCaptionAboveOf(Object value) {
        return ((TemplateItem)value).getGroupName();
      }
    }));

    myFilteringListModel.setFilter(new Condition<TemplateItem>() {
      @Override
      public boolean value(TemplateItem item) {
        return item.getMatchingDegree() > Integer.MIN_VALUE;
      }
    });

    myList.setModel(myFilteringListModel);
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String text = "*" + mySearchField.getText().trim();
        myMatcher = NameUtil.buildMatcher(text, NameUtil.MatchingCaseSensitivity.NONE);

        TemplateItem value = (TemplateItem)myList.getSelectedValue();
        int degree = value == null ? Integer.MIN_VALUE : value.getMatchingDegree();
        myBestMatch = Pair.create(degree > Integer.MIN_VALUE ? value : null, degree);

        myFilteringListModel.refilter();
        if (myBestMatch.first != null) {
          myList.setSelectedValue(myBestMatch.first, true);
        }
      }
    });

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        InputEvent event = e.getInputEvent();
        if (event instanceof KeyEvent) {
          int row = myList.getSelectedIndex();
           int toSelect;
           switch (((KeyEvent)event).getKeyCode()) {
             case KeyEvent.VK_UP:
               toSelect = row == 0 ? myList.getItemsCount() - 1 : row - 1;
               myList.setSelectedIndex(toSelect);
               myList.ensureIndexIsVisible(toSelect);
               break;
             case KeyEvent.VK_DOWN:
               toSelect = row < myList.getItemsCount() - 1 ? row + 1 : 0;
               myList.setSelectedIndex(toSelect);
               myList.ensureIndexIsVisible(toSelect);
               break;
           }
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_UP, KeyEvent.VK_DOWN), mySearchField);
  }

  void resetSelection() {
    SelectTemplateSettings settings = SelectTemplateSettings.getInstance();
    if (settings.getLastGroup() == null || !setSelectedTemplate(settings.getLastGroup(), settings.getLastTemplate())) {
      myList.setSelectedIndex(0);
    }
  }

  void saveSelection() {
    TemplateItem item = (TemplateItem)myList.getSelectedValue();
    if (item != null) {
      SelectTemplateSettings.getInstance().setLastTemplate(item.getGroupName(), item.getName());
    }
  }

  private List<TemplateItem> buildItems(MultiMap<TemplatesGroup, ProjectTemplate> map) {
    List<TemplateItem> items = new ArrayList<TemplateItem>();
    List<TemplatesGroup> groups = new ArrayList<TemplatesGroup>(map.keySet());
    Collections.sort(groups, new Comparator<TemplatesGroup>() {
      @Override
      public int compare(TemplatesGroup o1, TemplatesGroup o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (TemplatesGroup group : groups) {
      for (ProjectTemplate template : map.get(group)) {
        TemplateItem templateItem = new TemplateItem(template, group);
        items.add(templateItem);
      }
    }
    return items;
  }

  @Nullable
  public ProjectTemplate getSelectedTemplate() {
    Object value = myList.getSelectedValue();
    return value instanceof TemplateItem ? ((TemplateItem)value).myTemplate : null;
  }

  public boolean setSelectedTemplate(@Nullable String group, @Nullable String name) {
    for (int i = 0; i < myList.getModel().getSize(); i++) {
      Object o = myList.getModel().getElementAt(i);
      if (o instanceof TemplateItem && ((TemplateItem)o).myGroup.getName().equals(group) && ((TemplateItem)o).getName().equals(name)) {
        myList.setSelectedIndex(i);
        return true;
      }
    }

    return false;
  }

  class TemplateItem {

    private final ProjectTemplate myTemplate;
    private final TemplatesGroup myGroup;

    TemplateItem(ProjectTemplate template, TemplatesGroup group) {
      myTemplate = template;
      myGroup = group;
    }

    String getName() {
      return myTemplate.getName();
    }

    public String getGroupName() {
      return myGroup.getName();
    }

    Icon getIcon() {
      return myTemplate.createModuleBuilder().getNodeIcon();
    }

    protected int getMatchingDegree() {
      if (myMatcher == null) return Integer.MAX_VALUE;
      int i = myMatcher.matchingDegree(getGroupName() + " " + getName());
      if (myBestMatch == null || i > myBestMatch.second) {
        myBestMatch = Pair.create(this, i);
      }
      return i;
    }
  }
}
