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
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StringInterner;
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
  private final StringInterner myInterner = new StringInterner();

  public RunConfigurationExtensionsManager(ExtensionPointName<T> extensionPointName) {
    myExtensionPointName = extensionPointName;
  }

  public void readExternal(@NotNull final U configuration,
                           @NotNull final Element parentNode) throws InvalidDataException {
    final List<Element> children = parentNode.getChildren(getExtensionRootAttr());
    final Map<String, T> extensions = ContainerUtil.newHashMap();
    for (T extension : getApplicableExtensions(configuration)) {
      extensions.put(extension.getSerializationId(), extension);
    }

    // if some of extensions settings weren't found we should just keep it because some plugin with extension
    // may be turned off
    boolean found = true;
    for (Object o : children) {
      final Element element = (Element)o;
      final String extensionName = element.getAttributeValue(getIdAttrName());
      final T extension = extensions.remove(extensionName);
      if (extension != null) {
        extension.readExternal(configuration, element);
      }
      else {
        found = false;
      }
    }
    if (!found) {
      List<Element> copy = new ArrayList<Element>(children.size());
      for (Element child : children) {
        Element clone = (Element)child.clone();
        JDOMUtil.internElement(clone, myInterner);
        copy.add(clone);
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

  public void writeExternal(@NotNull final U configuration,
                            @NotNull final Element parentNode) throws WriteExternalException {
    final TreeMap<String, Element> map = ContainerUtil.newTreeMap();
    final List<Element> elements = configuration.getCopyableUserData(RUN_EXTENSIONS);
    if (elements != null) {
      for (Element el : elements) {
        final String name = el.getAttributeValue(getIdAttrName());
        map.put(name, (Element)el.clone());
      }
    }

    for (T extension : getApplicableExtensions(configuration)) {
      Element el = new Element(getExtensionRootAttr());
      el.setAttribute(getIdAttrName(), extension.getSerializationId());
      try {
        extension.writeExternal(configuration, el);
      }
      catch (WriteExternalException e) {
        map.remove(extension.getSerializationId());
        continue;
      }
      map.put(extension.getSerializationId(), el);
    }

    for (Element val : map.values()) {
      parentNode.addContent(val);
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

  private List<T> getApplicableExtensions(@NotNull final U configuration) {
    final List<T> extensions = new ArrayList<T>();
    for (T extension : Extensions.getExtensions(myExtensionPointName)) {
      if (extension.isApplicableFor(configuration)) {
        extensions.add(extension);
      }
    }
    return extensions;
  }

  private List<T> getEnabledExtensions(@NotNull final U configuration, @Nullable RunnerSettings runnerSettings) {
    final List<T> extensions = new ArrayList<T>();
    for (T extension : Extensions.getExtensions(myExtensionPointName)) {
      if (extension.isApplicableFor(configuration) && extension.isEnabledFor(configuration, runnerSettings)) {
        extensions.add(extension);
      }
    }
    return extensions;
  }
}
