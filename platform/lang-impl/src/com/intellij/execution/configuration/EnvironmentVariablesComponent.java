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

/*
 * User: anna
 * Date: 14-May-2007
 */
package com.intellij.execution.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.UserActivityProviderComponent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.ChangeListener;
import java.util.Map;

public class EnvironmentVariablesComponent extends LabeledComponent<TextFieldWithBrowseButton> implements UserActivityProviderComponent {
  @NonNls private static final String ENVS = "envs";
  @NonNls public static final String ENV = "env";
  @NonNls public static final String NAME = "name";
  @NonNls public static final String VALUE = "value";
  @NonNls private static final String OPTION = "option";
  @NonNls private static final String ENV_VARIABLES = "ENV_VARIABLES";

  private final EnvironmentVariablesTextFieldWithBrowseButton myEnvVars;

  public EnvironmentVariablesComponent() {
    super();
    myEnvVars = new EnvironmentVariablesTextFieldWithBrowseButton();
    setComponent(myEnvVars);
    setText(ExecutionBundle.message("environment.variables.component.title"));
  }

  public void setEnvs(@NotNull Map<String, String> envs) {
    myEnvVars.setEnvs(envs);
  }

  @NotNull
  public Map<String, String> getEnvs() {
    return myEnvVars.getEnvs();
  }

  public boolean isPassParentEnvs() {
    return myEnvVars.isPassParentEnvs();
  }

  public void setPassParentEnvs(final boolean passParentEnvs) {
    myEnvVars.setPassParentEnvs(passParentEnvs);
  }

  @NotNull
  public EnvironmentVariablesData getEnvData() {
    return myEnvVars.getData();
  }

  public void setEnvData(@NotNull EnvironmentVariablesData envData) {
    myEnvVars.setData(envData);
  }

  public static void readExternal(Element element, Map<String, String> envs) {
    final Element envsElement = element.getChild(ENVS);
    if (envsElement != null) {
      for (Object o : envsElement.getChildren(ENV)) {
        Element envElement = (Element)o;
        final String envName = envElement.getAttributeValue(NAME);
        final String envValue = envElement.getAttributeValue(VALUE);
        if (envName != null && envValue != null) {
          envs.put(envName, envValue);
        }
      }
    } else { //compatibility with prev version
      for (Object o : element.getChildren(OPTION)) {
        if (Comparing.strEqual(((Element)o).getAttributeValue(NAME), ENV_VARIABLES)) {
          splitVars(envs, ((Element)o).getAttributeValue(VALUE));
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

  public static void writeExternal(@NotNull Element element, @NotNull Map<String, String> envs) {
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
  public void addChangeListener(final ChangeListener changeListener) {
    myEnvVars.addChangeListener(changeListener);
  }

  @Override
  public void removeChangeListener(final ChangeListener changeListener) {
    myEnvVars.removeChangeListener(changeListener);
  }
}
