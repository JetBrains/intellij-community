/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.configuration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author traff
 */
public class RunConfigurationExtensionsManager<U extends RunConfigurationBase, T extends RunConfigurationExtensionBase<U>> {
  public static final Key<List<Element>> RUN_EXTENSIONS = Key.create("run.extension.elements");
  private static final String EXT_ID_ATTR = "ID";
  private static final String EXTENSION_ROOT_ATTR = "EXTENSION";

  protected final ExtensionPointName<T> myExtensionPointName;

  public RunConfigurationExtensionsManager(ExtensionPointName<T> extensionPointName) {
    myExtensionPointName = extensionPointName;
  }

  public void readExternal(@NotNull U configuration, @NotNull Element parentNode) throws InvalidDataException {
    Map<String, T> extensions = new THashMap<>();
    for (T extension : getApplicableExtensions(configuration)) {
      extensions.put(extension.getSerializationId(), extension);
    }

    List<Element> children = parentNode.getChildren(getExtensionRootAttr());
    // if some of extensions settings weren't found we should just keep it because some plugin with extension
    // may be turned off
    boolean found = true;
    for (Element element : children) {
      final T extension = extensions.remove(element.getAttributeValue(getIdAttrName()));
      if (extension != null) {
        extension.readExternal(configuration, element);
      }
      else {
        found = false;
      }
    }
    if (!found) {
      List<Element> copy = new ArrayList<>(children.size());
      for (Element child : children) {
        copy.add(child.clone());
      }
      configuration.putCopyableUserData(RUN_EXTENSIONS, copy);
    }
  }

  protected String getIdAttrName() {
    return EXT_ID_ATTR;
  }

  protected String getExtensionRootAttr() {
    return EXTENSION_ROOT_ATTR;
  }

  public void writeExternal(@NotNull U configuration, @NotNull Element parentNode) {
    final TreeMap<String, Element> map = ContainerUtil.newTreeMap();
    final List<Element> elements = configuration.getCopyableUserData(RUN_EXTENSIONS);
    if (elements != null) {
      for (Element element : elements) {
        map.put(element.getAttributeValue(getIdAttrName()), element.clone());
      }
    }

    for (T extension : getApplicableExtensions(configuration)) {
      Element element = new Element(getExtensionRootAttr());
      element.setAttribute(getIdAttrName(), extension.getSerializationId());
      try {
        extension.writeExternal(configuration, element);
      }
      catch (WriteExternalException ignored) {
        continue;
      }

      if (!element.getContent().isEmpty() || element.getAttributes().size() > 1) {
        map.put(extension.getSerializationId(), element);
      }
    }

    for (Element values : map.values()) {
      parentNode.addContent(values);
    }
  }

  public <V extends U> void appendEditors(@NotNull final U configuration,
                                          @NotNull final SettingsEditorGroup<V> group) {
    for (T extension : getApplicableExtensions(configuration)) {
      @SuppressWarnings("unchecked")
      final SettingsEditor<V> editor = extension.createEditor((V)configuration);
      if (editor != null) {
        group.addEditor(extension.getEditorTitle(), editor);
      }
    }
  }

  public void validateConfiguration(@NotNull final U configuration,
                                    final boolean isExecution) throws Exception {
    // only for enabled extensions
    for (T extension : getEnabledExtensions(configuration, null)) {
      extension.validateConfiguration(configuration, isExecution);
    }
  }

  public void extendCreatedConfiguration(@NotNull final U configuration,
                                         @NotNull final Location location) {
    for (T extension : getApplicableExtensions(configuration)) {
      extension.extendCreatedConfiguration(configuration, location);
    }
  }

  public void extendTemplateConfiguration(@NotNull final U configuration) {
    for (T extension : getApplicableExtensions(configuration)) {
      extension.extendTemplateConfiguration(configuration);
    }
  }

  public void patchCommandLine(@NotNull final U configuration,
                               final RunnerSettings runnerSettings,
                               @NotNull final GeneralCommandLine cmdLine,
                               @NotNull final String runnerId) throws ExecutionException {
    // only for enabled extensions
    for (T extension : getEnabledExtensions(configuration, runnerSettings)) {
      extension.patchCommandLine(configuration, runnerSettings, cmdLine, runnerId);
    }
  }

  public void attachExtensionsToProcess(@NotNull final U configuration,
                                        @NotNull final ProcessHandler handler,
                                        RunnerSettings runnerSettings) {
    // only for enabled extensions
    for (T extension : getEnabledExtensions(configuration, runnerSettings)) {
      extension.attachToProcess(configuration, handler, runnerSettings);
    }
  }

  protected List<T> getApplicableExtensions(@NotNull U configuration) {
    List<T> extensions = new SmartList<>();
    for (T extension : Extensions.getExtensions(myExtensionPointName)) {
      if (extension.isApplicableFor(configuration)) {
        extensions.add(extension);
      }
    }
    return extensions;
  }

  protected List<T> getEnabledExtensions(@NotNull U configuration, @Nullable RunnerSettings runnerSettings) {
    List<T> extensions = new SmartList<>();
    for (T extension : Extensions.getExtensions(myExtensionPointName)) {
      if (extension.isApplicableFor(configuration) && extension.isEnabledFor(configuration, runnerSettings)) {
        extensions.add(extension);
      }
    }
    return extensions;
  }
}
