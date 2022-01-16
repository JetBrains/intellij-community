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

import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.Configurable.VariableProjectAppLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
  private final JPanel myProvidersPanel = new JPanel(new VerticalLayout(0));

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
    for (AutoImportOptionsProvider provider : providers) {
      JComponent component = provider.createComponent();
      assert component != null : "AutoImportOptionsProvider " + provider.getClass() + " has a null component.";
      if (!(component instanceof DialogPanel)) {
        component.setBorder(JBUI.Borders.merge(component.getBorder(), JBUI.Borders.emptyBottom(10), true));
      }
      myProvidersPanel.add(component, VerticalLayout.TOP);
    }
    return myProvidersPanel;
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
