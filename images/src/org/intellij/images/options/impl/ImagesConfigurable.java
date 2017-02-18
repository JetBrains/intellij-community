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
package org.intellij.images.options.impl;

import com.intellij.openapi.options.BaseConfigurableWithChangeSupport;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.intellij.images.ImagesBundle;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Configurable for Options.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ImagesConfigurable extends BaseConfigurableWithChangeSupport implements SearchableConfigurable, PropertyChangeListener {
  private static final String DISPLAY_NAME = ImagesBundle.message("settings.page.name");
  private ImagesOptionsComponent myComponent;

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getHelpTopic() {
    return "preferences.images";
  }

  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = new ImagesOptionsComponent();
      Options options = OptionsManager.getInstance().getOptions();
      options.addPropertyChangeListener(this);
      myComponent.getOptions().inject(options);
      myComponent.updateUI();
      myComponent.getOptions().addPropertyChangeListener(this);
      setModified(false);
    }
    return myComponent.getContentPane();
  }

  public void apply() {
    if (myComponent != null) {
      Options options = OptionsManager.getInstance().getOptions();
      options.inject(myComponent.getOptions());
    }
  }

  public void reset() {
    if (myComponent != null) {
      Options options = OptionsManager.getInstance().getOptions();
      myComponent.getOptions().inject(options);
      myComponent.updateUI();
    }
  }

  public void disposeUIResources() {
    if (myComponent != null) {
      Options options = OptionsManager.getInstance().getOptions();
      options.removePropertyChangeListener(this);
      myComponent.getOptions().removePropertyChangeListener(this);
      myComponent = null;
    }
  }

  public void propertyChange(PropertyChangeEvent evt) {
    Options options = OptionsManager.getInstance().getOptions();
    Options uiOptions = myComponent.getOptions();

    setModified(!options.equals(uiOptions));
  }

  public static void show(Project project) {
    ShowSettingsUtil.getInstance().editConfigurable(project, new ImagesConfigurable());
  }

  @NotNull
  @NonNls
  public String getId() {
    return "Images";
  }
}
