/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.externalDependencies.ProjectExternalDependency;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * @author nik
 */
public class ExternalDependenciesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final ExternalDependenciesManager myDependenciesManager;
  private CollectionListModel<ProjectExternalDependency> myListModel = new CollectionListModel<>();
  private Map<String, String> myPluginNameById;

  public ExternalDependenciesConfigurable(Project project) {
    myDependenciesManager = ExternalDependenciesManager.getInstance(project);
  }

  @Override
  public void reset() {
    List<ProjectExternalDependency> dependencies = myDependenciesManager.getAllDependencies();
    myListModel.replaceAll(dependencies);
  }

  @Override
  public boolean isModified() {
    return !new HashSet<>(myDependenciesManager.getAllDependencies()).equals(new HashSet<>(myListModel.getItems()));
  }

  @Override
  public void apply() throws ConfigurationException {
    myDependenciesManager.setAllDependencies(myListModel.getItems());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Required Plugins";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    final JBList dependenciesList = new JBList();
    dependenciesList.setCellRenderer(new ColoredListCellRendererWrapper<DependencyOnPlugin>() {
      @Override
      protected void doCustomize(JList list, DependencyOnPlugin value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          append(getPluginNameById(value.getPluginId()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          String minVersion = value.getMinVersion();
          String maxVersion = value.getMaxVersion();
          if (minVersion != null || maxVersion != null) {
            append(", version ");
          }
          if (minVersion != null && minVersion.equals(maxVersion)) {
            append(minVersion, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          else if (minVersion != null && maxVersion != null) {
            append("between ");
            append(minVersion, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            append(" and ");
            append(maxVersion, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          else if (minVersion != null) {
            append("at least ");
            append(minVersion, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          else if (maxVersion != null) {
            append("at most ");
            append(maxVersion, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
        }
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        return editSelectedDependency(dependenciesList);
      }
    }.installOn(dependenciesList);

    dependenciesList.setModel(myListModel);
    JPanel dependenciesPanel = ToolbarDecorator.createDecorator(dependenciesList)
      .disableUpDownActions()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          replaceDependency(new DependencyOnPlugin("", null, null, null), dependenciesList);
        }
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          editSelectedDependency(dependenciesList);
        }
      })
      .createPanel();
    return FormBuilder.createFormBuilder()
      .addLabeledComponentFillVertically("Plugins which are required to work on this project.", dependenciesPanel)
      .getPanel();
  }

  public boolean editSelectedDependency(JBList dependenciesList) {
    DependencyOnPlugin selected = (DependencyOnPlugin)dependenciesList.getSelectedValue();
    if (selected != null) {
      replaceDependency(selected, dependenciesList);
      return true;
    }
    return false;
  }

  private void replaceDependency(DependencyOnPlugin original, JBList dependenciesList) {
    DependencyOnPlugin dependency = editPluginDependency(dependenciesList, original);
    if (dependency != null) {
      for (ProjectExternalDependency dependency1 : new ArrayList<>(myListModel.getItems())) {
        if (dependency1 instanceof DependencyOnPlugin && ((DependencyOnPlugin)dependency1).getPluginId().equals(dependency.getPluginId())) {
          myListModel.remove(dependency1);
        }
      }
      myListModel.add(dependency);
      dependenciesList.setSelectedValue(dependency, true);
    }
  }

  private String getPluginNameById(@NotNull String pluginId) {
    return ObjectUtils.notNull(getPluginNameByIdMap().get(pluginId), pluginId);
  }

  private Map<String, String> getPluginNameByIdMap() {
    if (myPluginNameById == null) {
      myPluginNameById = new HashMap<>();
      for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
        myPluginNameById.put(descriptor.getPluginId().getIdString(), descriptor.getName());
      }
    }
    return myPluginNameById;
  }

  @NotNull
  @Override
  public String getId() {
    return "preferences.externalDependencies";
  }

  @Nullable
  private DependencyOnPlugin editPluginDependency(@NotNull JComponent parent, @NotNull final DependencyOnPlugin original) {
    List<String> pluginIds = new ArrayList<>(getPluginNameByIdMap().keySet());
    if (!original.getPluginId().isEmpty() && !pluginIds.contains(original.getPluginId())) {
      pluginIds.add(original.getPluginId());
    }
    Collections.sort(pluginIds, (o1, o2) -> getPluginNameById(o1).compareToIgnoreCase(getPluginNameById(o2)));

    final ComboBox pluginChooser = new ComboBox(ArrayUtilRt.toStringArray(pluginIds), 250);
    pluginChooser.setRenderer(new ListCellRendererWrapper<String>() {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        setText(getPluginNameById(value));
      }
    });
    new ComboboxSpeedSearch(pluginChooser) {
      @Override
      protected String getElementText(Object element) {
        return getPluginNameById((String)element);
      }
    };
    pluginChooser.setSelectedItem(original.getPluginId());

    final JBTextField minVersionField = new JBTextField(StringUtil.notNullize(original.getMinVersion()));
    final JBTextField maxVersionField = new JBTextField(StringUtil.notNullize(original.getMaxVersion()));
    final JBTextField channelField = new JBTextField(StringUtil.notNullize(original.getChannel()));
    minVersionField.getEmptyText().setText("<any>");
    minVersionField.setColumns(10);
    maxVersionField.getEmptyText().setText("<any>");
    maxVersionField.setColumns(10);
    channelField.setColumns(10);
    JPanel panel = FormBuilder.createFormBuilder()
      .addLabeledComponent("Plugin:", pluginChooser)
      .addLabeledComponent("Minimum version:", minVersionField)
      .addLabeledComponent("Maximum version:", maxVersionField)
      .addLabeledComponent("Channel:", channelField)
      .getPanel();
    final DialogBuilder dialogBuilder = new DialogBuilder(parent).title("Required Plugin").centerPanel(panel);
    dialogBuilder.setPreferredFocusComponent(pluginChooser);
    pluginChooser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        dialogBuilder.setOkActionEnabled(!StringUtil.isEmpty((String)pluginChooser.getSelectedItem()));
      }
    });
    if (dialogBuilder.show() == DialogWrapper.OK_EXIT_CODE) {
      return new DependencyOnPlugin(((String)pluginChooser.getSelectedItem()),
                                    StringUtil.nullize(minVersionField.getText().trim()),
                                    StringUtil.nullize(maxVersionField.getText().trim()),
                                    StringUtil.nullize(channelField.getText().trim()));
    }
    return null;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "Required_Plugin";
  }

  @Override
  public void disposeUIResources() {
  }
}
