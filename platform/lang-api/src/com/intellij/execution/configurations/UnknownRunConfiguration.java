/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author spleaner
 */
public class UnknownRunConfiguration implements RunConfiguration, WithoutOwnBeforeRunSteps {
  private final ConfigurationFactory myFactory;
  private Element myStoredElement;
  private String myName;
  private final Project myProject;

  private static final AtomicInteger myUniqueName = new AtomicInteger(1);
  private boolean myDoNotStore;

  public UnknownRunConfiguration(@NotNull final ConfigurationFactory factory, @NotNull final Project project) {
    myFactory = factory;
    myProject = project;
  }

  public void setDoNotStore(boolean b) {
    myDoNotStore = b;
  }

  @Override
  @Nullable
  public Icon getIcon() {
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
  public void setName(final String name) {
    myName = name;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new UnknownSettingsEditor();
  }

  @Override
  public Project getProject() {
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
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    String factoryName = "";
    if (myStoredElement != null) {
      factoryName = myStoredElement.getAttributeValue("type");
    }
    throw new ExecutionException("Unknown run configuration type" + (factoryName.isEmpty() ? "" : " " + factoryName));
  }

  @Override
  public String getName() {
    if (myName == null) {
      myName = String.format("Unknown%s", myUniqueName.getAndAdd(1));
    }

    return myName;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    throw new RuntimeConfigurationException("Broken configuration due to unavailable plugin or invalid configuration data.");
  }

  @Override
  public void readExternal(@NotNull final Element element) throws InvalidDataException {
    myStoredElement = JDOMUtil.internElement(element);
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    if (myStoredElement != null) {
      for (Attribute a : myStoredElement.getAttributes()) {
        element.setAttribute(a.getName(), a.getValue());
      }

      for (Element child : myStoredElement.getChildren()) {
        element.addContent(child.clone());
      }
    }
  }

  private static class UnknownSettingsEditor extends SettingsEditor<UnknownRunConfiguration> {
    private final JPanel myPanel;

    private UnknownSettingsEditor() {
      myPanel = new JPanel();
      myPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 50, 0));

      myPanel.add(new JLabel("This configuration cannot be edited", SwingConstants.CENTER));
    }

    @Override
    protected void resetEditorFrom(@NotNull final UnknownRunConfiguration s) {
    }

    @Override
    protected void applyEditorTo(@NotNull final UnknownRunConfiguration s) throws ConfigurationException {
    }

    @Override
    @NotNull
    protected JComponent createEditor() {
      return myPanel;
    }
  }
}
