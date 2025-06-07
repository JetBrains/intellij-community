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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.roots.ui.configuration.classpath.ClasspathPanelImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ClasspathEditor extends ModuleElementsEditor implements ModuleRootListener {

  private ClasspathPanelImpl myPanel;
  private ClasspathFormatUI myClasspathFormatUI;
  private boolean myDisposed;

  public ClasspathEditor(final ModuleConfigurationState state) {
    super(state);

    final Disposable disposable = Disposer.newDisposable();
    state.getProject().getMessageBus().connect(disposable).subscribe(TOPIC, this);
    registerDisposable(disposable);
  }

  @Override
  public boolean isModified() {
    return super.isModified() || (myClasspathFormatUI != null && myClasspathFormatUI.isModified());
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
    if (myClasspathFormatUI != null) {
      myClasspathFormatUI.apply();
    }
  }

  @Override
  public void canApply() throws ConfigurationException {
    super.canApply();
    if (myClasspathFormatUI != null) {
      myClasspathFormatUI.canApply();
    }
  }

  @Override
  public JComponent createComponentImpl() {
    myPanel = new ClasspathPanelImpl(getState());
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP));
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
      myClasspathFormatUI = new ClasspathFormatUI(providers, getState());
      panel.add(myClasspathFormatUI.getPanel(), BorderLayout.SOUTH);
    }

    return panel;
  }

  private ModifiableRootModel getModifiableModel() {
    return getState().getModifiableRootModel();
  }

  public void selectOrderEntry(final @NotNull OrderEntry entry) {
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

  public void setSdk(final @Nullable Sdk newJDK) {
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

  public static @NlsContexts.ConfigurableName String getName() {
    return JavaCompilerBundle.message("modules.classpath.title");
  }
}