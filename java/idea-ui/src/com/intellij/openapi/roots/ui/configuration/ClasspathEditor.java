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
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.ConfigurationException;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClasspathEditor extends ModuleElementsEditor implements ModuleRootListener {

  private ClasspathPanelImpl myPanel;
  private ClasspathFormatPanel myClasspathFormatPanel;
  private boolean myDisposed;

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
    return getName();
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
      myClasspathFormatPanel.canApply();
    }
  }

  @Override
  public JComponent createComponentImpl() {
    myPanel = new ClasspathPanelImpl(getState());
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    panel.add(myPanel, BorderLayout.CENTER);

    final ModuleJdkConfigurable jdkConfigurable =
      new ModuleJdkConfigurable(this, ((ModulesConfigurator)getState().getModulesProvider()).getProjectStructureConfigurable()) {
        @Override
        protected ModifiableRootModel getRootModel() {
          return getModifiableModel();
        }
      };
    panel.add(jdkConfigurable.createComponent(), BorderLayout.NORTH);
    jdkConfigurable.reset();
    registerDisposable(jdkConfigurable);

    ClasspathStorageProvider[] providers = ClasspathStorageProvider.EXTENSION_POINT_NAME.getExtensions();
    if (providers.length > 0) {
      myClasspathFormatPanel = new ClasspathFormatPanel(providers, getState());
      panel.add(myClasspathFormatPanel, BorderLayout.SOUTH);
    }

    return panel;
  }

  private ModifiableRootModel getModifiableModel() {
    return getState().getModifiableRootModel();
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
  public void rootsChanged(@NotNull ModuleRootEvent event) {
    if (myPanel != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myDisposed) {
          myPanel.rootsChanged();
        }
      });
    }
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    myDisposed = true;
  }

  public void setSdk(@Nullable final Sdk newJDK) {
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

  private static final class ClasspathFormatPanel extends JPanel {
    private final @NotNull ModuleConfigurationState myState;
    private final JComboBox<String> comboBoxClasspathFormat;

    private ClasspathFormatPanel(ClasspathStorageProvider[] providers, @NotNull ModuleConfigurationState state) {
      super(new GridBagLayout());
      myState = state;

      JLabel comboBoxClasspathFormatLabel = new JLabel(JavaUiBundle.message("project.roots.classpath.format.label"));
      add(comboBoxClasspathFormatLabel,
          new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(10, 6, 6, 0), 0, 0));

      Map<String, String> formatIdToDescription = new LinkedHashMap<>();
      formatIdToDescription.put(ClassPathStorageUtil.DEFAULT_STORAGE, JavaUiBundle.message("project.roots.classpath.format.default.descr"));
      for (ClasspathStorageProvider provider : providers) {
        formatIdToDescription.put(provider.getID(), provider.getDescription());
      }
      comboBoxClasspathFormat = new ComboBox<>(ArrayUtilRt.toStringArray(formatIdToDescription.keySet()));
      comboBoxClasspathFormat.setRenderer(SimpleListCellRenderer.create("", formatIdToDescription::get));
      comboBoxClasspathFormat.setSelectedItem(getModuleClasspathFormat());
      comboBoxClasspathFormatLabel.setLabelFor(comboBoxClasspathFormat);
      add(comboBoxClasspathFormat,
          new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(6, 6, 6, 0), 0, 0));
    }

    private String getSelectedClasspathFormat() {
      return (String)comboBoxClasspathFormat.getSelectedItem();
    }

    private @NlsContexts.Label String getModuleClasspathFormat() {
      @NlsSafe final String type = ClassPathStorageUtil.getStorageType(myState.getCurrentRootModel().getModule());
      return type;
    }

    private boolean isModified() {
      return !getSelectedClasspathFormat().equals(getModuleClasspathFormat());
    }

    public void canApply() throws ConfigurationException {
      ClasspathStorageProvider provider = ClasspathStorage.getProvider(getSelectedClasspathFormat());
      if (provider != null) {
        provider.assertCompatible(myState.getCurrentRootModel());
      }
    }

    private void apply() throws ConfigurationException {
      canApply();
      ClasspathStorage.setStorageType(myState.getCurrentRootModel(), getSelectedClasspathFormat());
    }
  }

  public static @NlsContexts.ConfigurableName String getName() {
    return JavaCompilerBundle.message("modules.classpath.title");
  }
}