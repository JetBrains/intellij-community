/*
 * Copyright 2004-2005 Alexey Efimov
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

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.roots.ui.configuration.classpath.ClasspathPanelImpl;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ClasspathEditor extends ModuleElementsEditor implements ModuleRootListener {
  public static final String NAME = ProjectBundle.message("modules.classpath.title");

  private ClasspathPanelImpl myPanel;

  private ClasspathFormatPanel myClasspathFormatPanel;

  public ClasspathEditor(final ModuleConfigurationState state) {
    super(state);

    final Disposable disposable = Disposer.newDisposable();

    state.getProject().getMessageBus().connect(disposable).subscribe(ProjectTopics.PROJECT_ROOTS, this);
    registerDisposable(disposable);
  }

  @Override
  public boolean isModified() {
    return super.isModified() || (myClasspathFormatPanel != null && myClasspathFormatPanel.isModified());
  }

  @Override
  public String getHelpTopic() {
    return "projectStructure.modules.dependencies";
  }

  @Override
  public String getDisplayName() {
    return NAME;
  }

  @Override
  public void saveData() {
    myPanel.stopEditing();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myClasspathFormatPanel != null) {
      myClasspathFormatPanel.apply();
    }
  }

  @Override
  public void canApply() throws ConfigurationException {
    super.canApply();
    if (myClasspathFormatPanel != null) {
      ClasspathStorageProvider provider = ClasspathStorage.getProvider(myClasspathFormatPanel.getSelectedClasspathFormat());
      if (provider != null) {
        provider.assertCompatible(getModel());
      }
    }
  }

  @Override
  public JComponent createComponentImpl() {
    myPanel = new ClasspathPanelImpl(getState());
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    panel.add(myPanel, BorderLayout.CENTER);

    final ModuleJdkConfigurable jdkConfigurable =
      new ModuleJdkConfigurable(this, ProjectStructureConfigurable.getInstance(myProject).getProjectJdksModel()) {
        @Override
        protected ModifiableRootModel getRootModel() {
          return getState().getRootModel();
        }
      };
    panel.add(jdkConfigurable.createComponent(), BorderLayout.NORTH);
    jdkConfigurable.reset();
    registerDisposable(jdkConfigurable);

    ClasspathStorageProvider[] providers = ClasspathStorageProvider.EXTENSION_POINT_NAME.getExtensions();
    if (providers.length > 0) {
      myClasspathFormatPanel = new ClasspathFormatPanel(providers);
      panel.add(myClasspathFormatPanel, BorderLayout.SOUTH);
    }

    return panel;
  }

  public void selectOrderEntry(@NotNull final OrderEntry entry) {
    myPanel.selectOrderEntry(entry);
  }

  @Override
  public void moduleStateChanged() {
    if (myPanel != null) {
      myPanel.initFromModel();
    }
  }

  @Override
  public void beforeRootsChange(ModuleRootEvent event) {
  }

  @Override
  public void rootsChanged(ModuleRootEvent event) {
    if (myPanel != null) {
      myPanel.rootsChanged();
    }
  }

  public void setSdk(final Sdk newJDK) {
    final ModifiableRootModel model = getModel();
    if (newJDK != null) {
      model.setSdk(newJDK);
    }
    else {
      model.inheritSdk();
    }

    if (myPanel != null) {
      myPanel.forceInitFromModel();
    }
  }

  private class ClasspathFormatPanel extends JPanel {
    private final JComboBox comboBoxClasspathFormat;

    private final Map<String,String> formatIdToDescription = new THashMap<>();

    private ClasspathFormatPanel(@NotNull ClasspathStorageProvider[] providers) {
      super(new GridBagLayout());

      add(new JLabel(ProjectBundle.message("project.roots.classpath.format.label")),
          new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(10, 6, 6, 0), 0, 0));

      formatIdToDescription.put(ClassPathStorageUtil.DEFAULT_STORAGE, ProjectBundle.message("project.roots.classpath.format.default.descr"));
      for (ClasspathStorageProvider provider : providers) {
        formatIdToDescription.put(provider.getID(), provider.getDescription());
      }

      comboBoxClasspathFormat = new ComboBox(formatIdToDescription.values().toArray());
      updateClasspathFormat();
      add(comboBoxClasspathFormat,
          new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(6, 6, 6, 0), 0, 0));
    }

    private void updateClasspathFormat() {
      comboBoxClasspathFormat.setSelectedItem(formatIdToDescription.get(getModuleClasspathFormat()));
    }

    private String getSelectedClasspathFormat() {
      final String selected = (String)comboBoxClasspathFormat.getSelectedItem();
      for ( Map.Entry<String,String> entry : formatIdToDescription.entrySet() ) {
        if ( entry.getValue().equals(selected)) {
          return entry.getKey();
        }
      }
      throw new IllegalStateException(selected);
    }

    @NotNull
    private String getModuleClasspathFormat() {
      return ClassPathStorageUtil.getStorageType(getModel().getModule());
    }

    boolean isModified() {
      return comboBoxClasspathFormat != null && !getSelectedClasspathFormat().equals(getModuleClasspathFormat());
    }

    void apply() throws ConfigurationException {
      final String storageID = getSelectedClasspathFormat();
      ClasspathStorageProvider provider = ClasspathStorage.getProvider(storageID);
      if (provider != null) {
        provider.assertCompatible(getModel());
      }
      ClasspathStorage.setStorageType(getModel(), storageID);
    }
  }
}
