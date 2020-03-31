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

package com.intellij.application.options.editor;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.Configurable.VariableProjectAppLevel;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class AutoImportOptionsConfigurable
  extends CompositeConfigurable<AutoImportOptionsProvider>
  implements EditorOptionsProvider, VariableProjectAppLevel, Configurable.WithEpDependencies {

  private final Project myProject;
  private JPanel myPanel;
  private JPanel myProvidersPanel;

  public AutoImportOptionsConfigurable(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  protected List<AutoImportOptionsProvider> createConfigurables() {
    return ContainerUtil.mapNotNull(AutoImportOptionsProviderEP.EP_NAME.getExtensions(myProject), ep -> ep.createConfigurable());
  }

  @Override
  @Nls
  public String getDisplayName() {
    return ApplicationBundle.message("auto.import");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.autoimport";
  }

  @Override
  public JComponent createComponent() {
    myProvidersPanel.removeAll();
    List<AutoImportOptionsProvider> providers = getConfigurables();
    for (int i = 0; i < providers.size(); i++) {
      AutoImportOptionsProvider provider = providers.get(i);
      JComponent component = provider.createComponent();
      assert component != null: "AutoImportOptionsProvider " + provider.getClass() + " has a null component.";
      myProvidersPanel.add(component, new GridBagConstraints(0, i, 1, 1, 0, 0,
                                                             GridBagConstraints.NORTH,
                                                             GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    }
    myProvidersPanel.add(Box.createVerticalGlue(), new GridBagConstraints(0, providers.size(), 1, 1, 0, 1,
                                                                          GridBagConstraints.NORTH,
                                                                          GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));
    myProvidersPanel.add(Box.createVerticalGlue(), new GridBagConstraints(1, 0, providers.size() + 1, 1, 1, 0,
                                                                          GridBagConstraints.NORTH,
                                                                          GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));
    return myPanel;
  }

  @Override
  @NotNull
  public String getId() {
    return "editor.preferences.import";
  }

  @Override
  public boolean isProjectLevel() {
    return false;
  }

  @NotNull
  @Override
  public Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singleton(AutoImportOptionsProviderEP.EP_NAME);
  }
}
