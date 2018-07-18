// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.externalDependencies.ProjectExternalDependency;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
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
  private static final Logger LOG = Logger.getInstance(ExternalDependenciesConfigurable.class);
  private final ExternalDependenciesManager myDependenciesManager;
  private final CollectionListModel<ProjectExternalDependency> myListModel = new CollectionListModel<>();
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
    JBList<ProjectExternalDependency> dependenciesList = new JBList<>();
    dependenciesList.setCellRenderer(new ColoredListCellRenderer<ProjectExternalDependency>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends ProjectExternalDependency> list, ProjectExternalDependency dependency,
                                           int index, boolean selected, boolean hasFocus) {
        if (dependency instanceof DependencyOnPlugin) {
          DependencyOnPlugin value = (DependencyOnPlugin)dependency;
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
        else {
          LOG.error("Unsupported external dependency: " + dependency.getClass());
          append(dependency.toString());
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
          replaceDependency(new DependencyOnPlugin("", null, null), dependenciesList);
        }
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          editSelectedDependency(dependenciesList);
        }
      })
      .createPanel();

    String text = XmlStringUtil.wrapInHtml("Specify a list of plugins required for your project. " +
                                           ApplicationNamesInfo.getInstance().getFullProductName() + " will notify you if a required plugin is missing or needs an update. ");
    return JBUI.Panels.simplePanel(0, UIUtil.DEFAULT_VGAP).addToCenter(dependenciesPanel).addToTop(new JBLabel(text));
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
        String idString = descriptor.getPluginId().getIdString();
        //todo[nik] change 'name' tag of the core plugin instead
        String name = PluginManagerCore.CORE_PLUGIN_ID.equals(idString) ? "IDE Core" : descriptor.getName();
        myPluginNameById.put(idString, name);
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

    ComboBox<String> pluginChooser = new ComboBox<>(ArrayUtilRt.toStringArray(pluginIds), 250);
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
    minVersionField.getEmptyText().setText("<any>");
    minVersionField.setColumns(10);
    maxVersionField.getEmptyText().setText("<any>");
    maxVersionField.setColumns(10);
    JPanel panel = FormBuilder.createFormBuilder()
      .addLabeledComponent("Plugin:", pluginChooser)
      .addLabeledComponent("Minimum version:", minVersionField)
      .addLabeledComponent("Maximum version:", maxVersionField)
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
                                    StringUtil.nullize(maxVersionField.getText().trim()));
    }
    return null;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "Required_Plugin";
  }
}
