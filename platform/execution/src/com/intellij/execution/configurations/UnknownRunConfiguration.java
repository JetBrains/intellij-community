// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.plugins.PluginFeatureService;
import com.intellij.ide.plugins.UltimateDependencyChecker;
import com.intellij.ide.plugins.PluginManagerConfigurableService;
import com.intellij.ide.plugins.advertiser.FeaturePluginData;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class UnknownRunConfiguration implements RunConfiguration, WithoutOwnBeforeRunSteps {
  private final ConfigurationFactory myFactory;
  private Element myStoredElement;
  private String myName;
  private final @NotNull Project myProject;

  private static final AtomicInteger myUniqueName = new AtomicInteger(1);
  private boolean myDoNotStore;

  public UnknownRunConfiguration(final @NotNull ConfigurationFactory factory, final @NotNull Project project) {
    myFactory = factory;
    myProject = project;
  }

  public void setDoNotStore(boolean b) {
    myDoNotStore = b;
  }

  @Override
  public @Nullable Icon getIcon() {
    return null;
  }

  public boolean isDoNotStore() {
    return myDoNotStore;
  }

  @Override
  public ConfigurationFactory getFactory() {
    return myFactory;
  }

  @Override
  public void setName(final @NotNull String name) {
    myName = name;
  }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new UnknownSettingsEditor();
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public RunConfiguration clone() {
    try {
      return (UnknownRunConfiguration)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  @Override
  public RunProfileState getState(final @NotNull Executor executor, final @NotNull ExecutionEnvironment env) throws ExecutionException {
    @NlsSafe String factoryName = getConfigurationTypeId();
    throw new ExecutionException(ExecutionBundle.message("dialog.message.unknown.run.configuration.type", factoryName, StringUtil.isEmpty(factoryName) ? 0 : 1));
  }

  @Override
  public @NotNull String getName() {
    if (myName == null) {
      myName = String.format("Unknown%s", myUniqueName.getAndAdd(1));
    }
    return myName;
  }

  private @Nullable String getConfigurationTypeId() {
    if (myStoredElement != null) {
      return myStoredElement.getAttributeValue("type");
    }
    return null;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    String typeId = getConfigurationTypeId();
    if (typeId == null) {
      return;
    }
    FeaturePluginData plugin = PluginFeatureService.Companion.__getPluginForFeature(RunManager.CONFIGURATION_TYPE_FEATURE_ID, typeId);
    if (plugin != null &&
        UltimateDependencyChecker.getInstance().canBeEnabled(plugin.pluginData.getPluginId())) {
      RuntimeConfigurationError err = new RuntimeConfigurationError(
        ExecutionBundle.message("dialog.message.broken.configuration.missing.plugin", plugin.displayName));
      err.setQuickFix(() -> {
        PluginManagerConfigurableService.getInstance().showPluginConfigurableAndEnable(null, plugin.pluginData.pluginIdString);
      });
      throw err;
    }
    throw new RuntimeConfigurationException(ExecutionBundle.message("dialog.message.broken.configuration"));
  }

  @Override
  public void readExternal(final @NotNull Element element) throws InvalidDataException {
    myStoredElement = JDOMUtil.internElement(element);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    if (myStoredElement != null) {
      for (Attribute a : myStoredElement.getAttributes()) {
        element.setAttribute(a.getName(), a.getValue());
      }

      for (Element child : myStoredElement.getChildren()) {
        element.addContent(child.clone());
      }
    }
  }

  private static final class UnknownSettingsEditor extends SettingsEditor<UnknownRunConfiguration> {
    private final JPanel myPanel;

    private UnknownSettingsEditor() {
      myPanel = new JPanel();
      myPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 50, 0));

      myPanel.add(new JLabel(ExecutionBundle.message("this.configuration.cannot.be.edited"), SwingConstants.CENTER));
    }

    @Override
    protected void resetEditorFrom(final @NotNull UnknownRunConfiguration s) {
    }

    @Override
    protected void applyEditorTo(final @NotNull UnknownRunConfiguration s) {
    }

    @Override
    protected @NotNull JComponent createEditor() {
      return myPanel;
    }
  }
}
