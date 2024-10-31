/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.execution.configuration;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.EnvFilesOptions;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.builder.VerticalComponentGap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.ChangeListener;
import java.util.List;
import java.util.Map;

public class EnvironmentVariablesComponent extends LabeledComponent<TextFieldWithBrowseButton>
  implements UserActivityProviderComponent {
  private static final @NonNls String ENVS = "envs";
  public static final @NonNls String ENV = "env";
  public static final @NonNls String NAME = "name";
  public static final @NonNls String VALUE = "value";
  private static final @NonNls String OPTION = "option";
  private static final @NonNls String ENV_VARIABLES = "ENV_VARIABLES";

  public final EnvironmentVariablesTextFieldWithBrowseButton myEnvVars;

  public EnvironmentVariablesComponent() {
    super();
    myEnvVars = createBrowseComponent();
    setComponent(myEnvVars);
    setText(ExecutionBundle.message("environment.variables.component.title"));
    putClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT, myEnvVars.getChildComponent());
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH);
  }

  protected @NotNull EnvironmentVariablesTextFieldWithBrowseButton createBrowseComponent() {
    return new EnvironmentVariablesTextFieldWithBrowseButton();
  }

  public void setEnvs(@NotNull Map<String, String> envs) {
    myEnvVars.setEnvs(envs);
  }

  public @NotNull Map<String, String> getEnvs() {
    return myEnvVars.getEnvs();
  }

  public boolean isPassParentEnvs() {
    return myEnvVars.isPassParentEnvs();
  }

  public void setPassParentEnvs(final boolean passParentEnvs) {
    myEnvVars.setPassParentEnvs(passParentEnvs);
  }

  public void setEnvFilePaths(List<String> envFilePaths) {
      myEnvVars.setEnvFilePaths(envFilePaths);
  }

  public List<String> getEnvFilePaths(){
    return myEnvVars.getEnvFilePaths();
  }

  public @NotNull EnvironmentVariablesData getEnvData() {
    return myEnvVars.getData();
  }

  public void setEnvData(@NotNull EnvironmentVariablesData envData) {
    myEnvVars.setData(envData);
  }

  public void reset(CommonProgramRunConfigurationParameters s) {
    setEnvs(s.getEnvs());
    setPassParentEnvs(s.isPassParentEnvs());
    if (s instanceof EnvFilesOptions) {
      myEnvVars.setEnvFilePaths(((EnvFilesOptions)s).getEnvFilePaths());
    }
  }

  public void apply(CommonProgramRunConfigurationParameters s) {
    s.setEnvs(getEnvs());
    s.setPassParentEnvs(isPassParentEnvs());
    if (s instanceof EnvFilesOptions) {
      ((EnvFilesOptions)s).setEnvFilePaths(myEnvVars.getEnvFilePaths());
    }
  }

  /**
   * Consider using {@link EnvironmentVariablesData#readExternal(Element)} instead for simplicity and better performance.
   */
  public static void readExternal(Element element, Map<String, String> envs) {
    final Element envsElement = element.getChild(ENVS);
    if (envsElement != null) {
      for (Element envElement : envsElement.getChildren(ENV)) {
        final String envName = envElement.getAttributeValue(NAME);
        final String envValue = envElement.getAttributeValue(VALUE);
        if (envName != null && envValue != null) {
          envs.put(envName, envValue);
        }
      }
    }
    else {
      //compatibility with the previous version
      for (Element o : element.getChildren(OPTION)) {
        if (Comparing.strEqual(o.getAttributeValue(NAME), ENV_VARIABLES)) {
          splitVars(envs, o.getAttributeValue(VALUE));
          break;
        }
      }
    }
  }

  private static void splitVars(final Map<String, String> envs, final String val) {
    if (val != null) {
      final String[] envVars = val.split(";");
      for (String envVar : envVars) {
        final int idx = envVar.indexOf('=');
        if (idx > -1) {
          envs.put(envVar.substring(0, idx), idx < envVar.length() - 1 ? envVar.substring(idx + 1) : "");
        }
      }
    }
  }

  /**
   * Consider using {@link EnvironmentVariablesData#writeExternal(Element)} instead for simplicity and better performance.
   */
  public static void writeExternal(@NotNull Element element, @NotNull Map<String, String> envs) {
    if (envs.isEmpty()) {
      return;
    }

    final Element envsElement = new Element(ENVS);
    for (String envName : envs.keySet()) {
      final Element envElement = new Element(ENV);
      envElement.setAttribute(NAME, envName);
      envElement.setAttribute(VALUE, envs.get(envName));
      envsElement.addContent(envElement);
    }
    element.addContent(envsElement);
  }

  @Override
  public void addChangeListener(final @NotNull ChangeListener changeListener) {
    myEnvVars.addChangeListener(changeListener);
  }

  @Override
  public void removeChangeListener(final @NotNull ChangeListener changeListener) {
    myEnvVars.removeChangeListener(changeListener);
  }
}
