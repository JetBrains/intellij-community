/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.01.2007
 * Time: 13:56:12
 */
package com.intellij.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.LayeredIcon;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class RunConfigurationExtension {
  public static final ExtensionPointName<RunConfigurationExtension> EP_NAME = new ExtensionPointName<RunConfigurationExtension>("com.intellij.runConfigurationExtension");
  public static final Key<List> RUN_EXTENSIONS = Key.create("run.extension.elemnts");
  public abstract void handleStartProcess(final ModuleBasedConfiguration configuration, final OSProcessHandler handler);
  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> SettingsEditor createEditor(T configuration);
  public abstract String getEditorTitle();
  public abstract String getName();
  @Nullable
  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> Icon getIcon(T runConfiguration);

  public static <T extends ModuleBasedConfiguration & RunJavaConfiguration> void appendEditors(T configuration, SettingsEditorGroup<T> group) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      final SettingsEditor editor = extension.createEditor(configuration);
      if (editor != null) {
        group.addEditor(extension.getEditorTitle(), editor);
      }
    }
  }

  public static <T extends ModuleBasedConfiguration & RunJavaConfiguration> Icon getIcon(T configuration, Icon icon) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      final Icon extIcon = extension.getIcon(configuration);
      if (extIcon != null) {
        return LayeredIcon.create(icon, extIcon);
      }
    }
    return icon;
  }

  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> void updateJavaParameters(final T configuration, final JavaParameters params, RunnerSettings runnerSettings);

  protected abstract void readExternal(ModuleBasedConfiguration runConfiguration, Element element) throws InvalidDataException;

  public static void readSettings(ModuleBasedConfiguration runConfiguration, Element parentNode) throws InvalidDataException {
    List children = parentNode.getChildren("extension");
    boolean found = true;
    ext: for (Object o : children) {
      final Element element = (Element)o;
      final String extensionName = element.getAttributeValue("name");
      for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
        if (Comparing.strEqual(extensionName, extension.getName())) {
          extension.readExternal(runConfiguration, element);
          continue ext;
        }
      }
      found = false;
    }
    if (!found) {
      runConfiguration.putCopyableUserData(RUN_EXTENSIONS, children);
    }
  }

  public static void writeSettings(ModuleBasedConfiguration runConfiguration, Element element) throws WriteExternalException {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      Element el = new Element("extension");
      el.setAttribute("name", extension.getName());
      extension.writeExternal(runConfiguration, el);
      element.addContent(el);
    }
    List<Element> elements = runConfiguration.getCopyableUserData(RUN_EXTENSIONS);
    if (elements != null && !elements.isEmpty()) {
      final List<String> foundNames = new ArrayList<String>();
      final List children = element.getChildren("extension");
      for (Object o : children) {
        foundNames.add(((Element)o).getAttributeValue("name"));
      }

      int idx = children.isEmpty() ? element.getContentSize() : element.indexOf((Content)children.get(0));
      for (Element el : elements) {
        final String name = el.getAttributeValue("name");
        int i = foundNames.indexOf(name);
        if (i == -1) {
          element.addContent(idx, (Content)el.clone());
        }
        idx ++;
      }
    }
  }

  protected abstract void writeExternal(ModuleBasedConfiguration runConfiguration, Element element) throws WriteExternalException;

  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> void patchConfiguration(T runJavaConfiguration);
  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> void checkConfiguration(T runJavaConfiguration) throws RuntimeConfigurationException;

  public static <T extends ModuleBasedConfiguration & RunJavaConfiguration> void patchCreatedConfiguration(T configuration) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      extension.patchConfiguration(configuration);
    }
  }

  public static <T extends ModuleBasedConfiguration & RunJavaConfiguration> void checkConfigurationIsValid(T configuration) throws RuntimeConfigurationException {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      extension.checkConfiguration(configuration);
    }
  }
}