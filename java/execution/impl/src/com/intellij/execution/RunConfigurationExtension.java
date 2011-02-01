/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.ui.LayeredIcon;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class RunConfigurationExtension {
  public static final ExtensionPointName<RunConfigurationExtension> EP_NAME = new ExtensionPointName<RunConfigurationExtension>("com.intellij.runConfigurationExtension");
  public static final Key<List> RUN_EXTENSIONS = Key.create("run.extension.elemnts");
  public abstract void handleStartProcess(final RunConfigurationBase configuration, final OSProcessHandler handler);
  @Nullable  
  public abstract SettingsEditor createEditor(RunConfigurationBase configuration);
  public abstract String getEditorTitle();
  public abstract String getName();
  @Nullable
  public abstract Icon getIcon(RunConfigurationBase runConfiguration);
  public abstract <T extends RunConfigurationBase > void updateJavaParameters(final T configuration, final JavaParameters params, RunnerSettings runnerSettings);

  protected abstract void readExternal(RunConfigurationBase runConfiguration, Element element) throws InvalidDataException;
  protected abstract void writeExternal(RunConfigurationBase runConfiguration, Element element) throws WriteExternalException;
  public abstract void patchConfiguration(RunConfigurationBase runJavaConfiguration);
  public abstract void checkConfiguration(RunConfigurationBase runJavaConfiguration) throws RuntimeConfigurationException;

  public RefactoringElementListener wrapElementListener(PsiElement element,
                                                        RunConfigurationBase runJavaConfiguration,
                                                        RefactoringElementListener listener) {
    return listener;
  }

  public static RefactoringElementListener wrapRefactoringElementListener(PsiElement element,
                                                                          RunConfigurationBase runConfigurationBase,
                                                                          RefactoringElementListener listener) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      listener = extension.wrapElementListener(element, runConfigurationBase, listener);
    }
    return listener;
  }

  public static  void appendEditors(RunConfigurationBase configuration, SettingsEditorGroup group) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      final SettingsEditor editor = extension.createEditor(configuration);
      if (editor != null) {
        group.addEditor(extension.getEditorTitle(), editor);
      }
    }
  }

  public static Icon getIcon(RunConfigurationBase configuration, Icon icon) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      final Icon extIcon = extension.getIcon(configuration);
      if (extIcon != null) {
        return LayeredIcon.create(icon, extIcon);
      }
    }
    return icon;
  }

  public static void readSettings(RunConfigurationBase runConfiguration, Element parentNode) throws InvalidDataException {
    final List children = parentNode.getChildren("extension");
    final Map<String, RunConfigurationExtension> extensions = new HashMap<String, RunConfigurationExtension>();
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      extensions.put(extension.getName(), extension);
    }
    for (Object o : children) {
      final Element element = (Element)o;
      final String extensionName = element.getAttributeValue("name");
      final RunConfigurationExtension extension = extensions.remove(extensionName);
      if (extension != null) {
        extension.readExternal(runConfiguration, element);
      }
    }
    //try to read from old format if possible
    for (RunConfigurationExtension extension : extensions.values()) {
      extension.readExternal(runConfiguration, parentNode);
    }
    runConfiguration.putCopyableUserData(RUN_EXTENSIONS, children);
  }

  public static void writeSettings(RunConfigurationBase runConfiguration, Element element) throws WriteExternalException {
    final TreeMap<String, Element> map = new TreeMap<String, Element>();
    final List<Element> elements = runConfiguration.getCopyableUserData(RUN_EXTENSIONS);
    if (elements != null) {
      for (Element el : elements) {
        final String name = el.getAttributeValue("name");
        map.put(name, (Element)el.clone());
      }
    }

    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      Element el = new Element("extension");
      el.setAttribute("name", extension.getName());
      try {
        extension.writeExternal(runConfiguration, el);
      }
      catch (WriteExternalException e) {
        map.remove(extension.getName());
        continue;
      }
      map.put(extension.getName(), el);
    }

    for (Element val : map.values()) {
      element.addContent(val);
    }
  }


  public static void patchCreatedConfiguration(RunConfigurationBase configuration) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      extension.patchConfiguration(configuration);
    }
  }

  public static void checkConfigurationIsValid(RunConfigurationBase configuration) throws RuntimeConfigurationException {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      extension.checkConfiguration(configuration);
    }
  }

  public  boolean isListenerDisabled(RunConfigurationBase configuration, Object listener) {
    return false;
  }
}