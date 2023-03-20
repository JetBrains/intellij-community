// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.externalDependencies.ProjectExternalDependency;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
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

public class ExternalDependenciesConfigurable implements SearchableConfigurable {
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
    return IdeBundle.message("configurable.ExternalDependenciesConfigurable.display.name");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JBList<ProjectExternalDependency> dependenciesList = new JBList<>();
    dependenciesList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends ProjectExternalDependency> list, ProjectExternalDependency dependency,
                                           int index, boolean selected, boolean hasFocus) {
        if (dependency instanceof DependencyOnPlugin value) {
          append(getPluginNameById(value.getPluginId()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          String minVersion = value.getMinVersion();
          String maxVersion = value.getMaxVersion();
          if (minVersion != null && minVersion.equals(maxVersion)) {
            append(IdeBundle.message("required.plugin.exact.version", minVersion));
          }
          else if (minVersion != null && maxVersion != null) {
            append(IdeBundle.message("required.plugin.between.versions", minVersion, maxVersion));
          }
          else if (minVersion != null) {
            append(IdeBundle.message("required.plugin.at.least.versions", minVersion));
          }
          else if (maxVersion != null) {
            append(IdeBundle.message("required.plugin.at.most.versions", maxVersion));
          }
        }
        else {
          LOG.error("Unsupported external dependency: " + dependency.getClass());
          @NlsSafe String dependencyDescription = dependency.toString();
          append(dependencyDescription);
        }
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
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

    String text = XmlStringUtil
      .wrapInHtml(IdeBundle.message("settings.required.plugins.title", ApplicationNamesInfo.getInstance().getFullProductName()));
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

  private @NlsContexts.ListItem String getPluginNameById(@NotNull @NlsSafe String pluginId) {
    return ObjectUtils.notNull(getPluginNameByIdMap().get(pluginId), pluginId);
  }

  private Map<String, @NlsContexts.ListItem String> getPluginNameByIdMap() {
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
    pluginIds.sort((o1, o2) -> getPluginNameById(o1).compareToIgnoreCase(getPluginNameById(o2)));

    ComboBox<String> pluginChooser = new ComboBox<>(ArrayUtilRt.toStringArray(pluginIds), 250);
    pluginChooser.setRenderer(SimpleListCellRenderer.create("", this::getPluginNameById));
    ComboboxSpeedSearch search = new ComboboxSpeedSearch(pluginChooser, null) {
      @Override
      protected String getElementText(Object element) {
        return getPluginNameById((String)element);
      }
    };
    search.setupListeners();
    pluginChooser.setSelectedItem(original.getPluginId());

    final JBTextField minVersionField = new JBTextField(StringUtil.notNullize(original.getRawMinVersion()));
    final JBTextField maxVersionField = new JBTextField(StringUtil.notNullize(original.getRawMaxVersion()));
    minVersionField.getEmptyText().setText(IdeBundle.message("label.version.any"));
    minVersionField.setColumns(17);
    maxVersionField.getEmptyText().setText(IdeBundle.message("label.version.any"));
    maxVersionField.setColumns(17);
    JPanel panel = FormBuilder.createFormBuilder()
      .addLabeledComponent(IdeBundle.message("label.plugin"), pluginChooser)
      .addLabeledComponent(IdeBundle.message("label.minimum.version"), minVersionField)
      .addLabeledComponent(IdeBundle.message("label.maximum.version"), maxVersionField)
      .getPanel();
    final DialogBuilder dialogBuilder = new DialogBuilder(parent).title(IdeBundle.message("dialog.title.required.plugin")).centerPanel(panel);
    dialogBuilder.setPreferredFocusComponent(pluginChooser);
    pluginChooser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        dialogBuilder.setOkActionEnabled(!StringUtil.isEmpty((String)pluginChooser.getSelectedItem()));
      }
    });
    dialogBuilder.setHelpId("Required_Plugin");
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
