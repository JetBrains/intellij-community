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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.ProjectTemplate;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
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
  private final FilteringListModel<Item> myFilteringListModel;
  private MinusculeMatcher myMatcher;
  private Pair<? extends Item, Integer> myBestMatch;

  public ProjectTypesList(JBList list, SearchTextField searchField, MultiMap<TemplatesGroup, ProjectTemplate> map) {
    myList = list;
    mySearchField = searchField;

    CollectionListModel<Item> model = new CollectionListModel<Item>(buildItems(map));
    myFilteringListModel = new FilteringListModel<Item>(model);

    myList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        Item item = (Item)value;
        boolean group = item instanceof GroupItem;
        append(item.getName(), group ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(item.getIcon());
        setIpad(group ? new Insets(2, 2, 2, 2) : new Insets(2, 20, 2, 2));
        //if (group && !selected) {
        //  setBackground(UIUtil.getPanelBackground());
        //}
      }
    });

    myFilteringListModel.setFilter(new Condition<Item>() {
      @Override
      public boolean value(Item item) {
        return item.getMatchingDegree() > Integer.MIN_VALUE;
      }
    });

    myList.setModel(myFilteringListModel);
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String text = "*" + mySearchField.getText().trim();
        myMatcher = NameUtil.buildMatcher(text, NameUtil.MatchingCaseSensitivity.NONE);

        Item value = (Item)myList.getSelectedValue();
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
    Item item = (Item)myList.getSelectedValue();
    if (item instanceof TemplateItem) {
      SelectTemplateSettings.getInstance().setLastTemplate(((TemplateItem)item).getGroupName(), item.getName());
    }
    else if (item instanceof GroupItem) {
      SelectTemplateSettings.getInstance().setLastTemplate(item.getName(), null);
    }
  }

  private List<? extends Item> buildItems(MultiMap<TemplatesGroup, ProjectTemplate> map) {
    List<Item> items = new ArrayList<Item>();
    List<TemplatesGroup> groups = new ArrayList<TemplatesGroup>(map.keySet());
    Collections.sort(groups, new Comparator<TemplatesGroup>() {
      @Override
      public int compare(TemplatesGroup o1, TemplatesGroup o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (TemplatesGroup group : groups) {
      GroupItem groupItem = new GroupItem(group);
      items.add(groupItem);
      for (ProjectTemplate template : map.get(group)) {
        TemplateItem templateItem = new TemplateItem(template, group);
        items.add(templateItem);
        groupItem.addChild(templateItem);
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

  abstract static class Item {

    abstract String getName();
    abstract Icon getIcon();

    protected abstract int getMatchingDegree();
  }

  class TemplateItem extends Item {

    private final ProjectTemplate myTemplate;
    private final TemplatesGroup myGroup;

    TemplateItem(ProjectTemplate template, TemplatesGroup group) {
      myTemplate = template;
      myGroup = group;
    }

    @Override
    String getName() {
      return myTemplate.getName();
    }

    String getGroupName() {
      return myGroup.getName();
    }

    @Override
    Icon getIcon() {
      return myTemplate.createModuleBuilder().getNodeIcon();
    }

    @Override
    protected int getMatchingDegree() {
      if (myMatcher == null) return Integer.MAX_VALUE;
      int i = myMatcher.matchingDegree(getName());
      if (myBestMatch == null || i > myBestMatch.second) {
        myBestMatch = Pair.create(this, i);
      }
      return i;
    }
  }

  class GroupItem extends Item {

    private final TemplatesGroup myGroup;
    private final List<TemplateItem> myChildren = new ArrayList<TemplateItem>();

    GroupItem(TemplatesGroup group) {
      myGroup = group;
    }

    @Override
    String getName() {
      return myGroup.getName();
    }

    @Override
    Icon getIcon() {
      return myGroup.getIcon();
    }

    @Override
    protected int getMatchingDegree() {
      return ContainerUtil.find(myChildren, new Condition<TemplateItem>() {
        @Override
        public boolean value(TemplateItem item) {
          return item.getMatchingDegree() > Integer.MIN_VALUE;
        }
      }) == null ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }

    public void addChild(TemplateItem item) {
      myChildren.add(item);
    }
  }
}
