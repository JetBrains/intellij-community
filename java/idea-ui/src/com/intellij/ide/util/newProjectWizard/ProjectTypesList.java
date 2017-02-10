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

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.templates.RemoteTemplatesFactory;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 11/21/12
 */
public class ProjectTypesList implements Disposable {

  private final JBList myList;
  private final CollectionListModel<TemplateItem> myModel;
  private MinusculeMatcher myMatcher;
  private Pair<TemplateItem, Integer> myBestMatch;

  private TemplateItem myLoadingItem;

  public ProjectTypesList(JBList list, MultiMap<TemplatesGroup, ProjectTemplate> map, final WizardContext context) {
    myList = list;

    new ListSpeedSearch(myList) {
      @Override
      protected String getElementText(Object element) {
        return super.getElementText(element);
      }
    }.setComparator(new SpeedSearchComparator(false));
    List<TemplateItem> items = buildItems(map);
    final TemplatesGroup samplesGroup = new TemplatesGroup("Loading Templates...", "", null, 0, null, null, null);
    myLoadingItem = new TemplateItem(new LoadingProjectTemplate(), samplesGroup) {
      @Override
      Icon getIcon() {
        return null;
      }

      @Override
      String getDescription() {
        return "";
      }
    };
    items.add(myLoadingItem);
    myModel = new CollectionListModel<>(items);

    final RemoteTemplatesFactory factory = new RemoteTemplatesFactory();
    ProgressManager.getInstance().run(new Task.Backgroundable(context.getProject(), "Loading Templates") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          myList.setPaintBusy(true);
          String[] groups = factory.getGroups();
          final List<TemplateItem> items = new ArrayList<>();
          for (String group : groups) {
            TemplatesGroup templatesGroup = new TemplatesGroup(group, "", factory.getGroupIcon(group), 0, null, null, null);
            ProjectTemplate[] templates = factory.createTemplates(group, context);
            for (ProjectTemplate template : templates) {
              items.add(new TemplateItem(template, templatesGroup));
            }
          }
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            int index = myList.getSelectedIndex();
            myModel.remove(myLoadingItem);
            myModel.add(items);
            myList.setSelectedIndex(index);
          });
        }
        finally {
          myList.setPaintBusy(false);
        }
      }
    });

    myList.setCellRenderer(new GroupedItemsListRenderer(new ListItemDescriptorAdapter() {
      @Nullable
      @Override
      public String getTextFor(Object value) {
        return ((TemplateItem)value).getName();
      }

      @Nullable
      @Override
      public Icon getIconFor(Object value) {
        return ((TemplateItem)value).getIcon();
      }

      @Override
      public boolean hasSeparatorAboveOf(Object value) {
        TemplateItem item = (TemplateItem)value;
        int index = myModel.getElementIndex(item);
        return index == 0 || !myModel.getElementAt(index - 1).getGroupName().equals(item.getGroupName());
      }

      @Nullable
      @Override
      public String getCaptionAboveOf(Object value) {
        return ((TemplateItem)value).getGroupName();
      }
    }));

    myList.setModel(myModel);
  }

  void installKeyAction(JComponent component) {
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
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_UP, KeyEvent.VK_DOWN), component);
  }

  void resetSelection() {
    if (myList.getSelectedIndex() != -1) return;
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
    List<TemplateItem> items = new ArrayList<>();
    List<TemplatesGroup> groups = new ArrayList<>(map.keySet());
    Collections.sort(groups);
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
        myList.ensureIndexIsVisible(i);
        return true;
      }
    }

    return false;
  }

  @Override
  public void dispose() {
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
      String text = getName() + " " + getGroupName();
      String description = getDescription();
      if (description != null) {
        text += " " + StringUtil.stripHtml(description, false);
      }
      int i = myMatcher.matchingDegree(text);
      if (myBestMatch == null || i > myBestMatch.second) {
        myBestMatch = Pair.create(this, i);
      }
      return i;
    }

    @Nullable
    String getDescription() {
      return myTemplate.getDescription();
    }

    @Override
    public String toString() {
      return getName() + " " + getGroupName();
    }
  }
}
