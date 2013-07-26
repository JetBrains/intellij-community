/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author yole
 */
public abstract class LocatableConfigurationBase extends RunConfigurationBase implements LocatableConfiguration {
  private static final String ATTR_NAME_CHANGED_BY_USER = "nameChangedByUser";

  protected LocatableConfigurationBase(Project project,
                                       ConfigurationFactory factory, String name) {
    super(project, factory, name);
  }

  private boolean myNameChangedByUser;

  @Override
  public boolean isGeneratedName() {
    return suggestedName() != null && !myNameChangedByUser;
  }

  /**
   * Renames the configuration to its suggested name.
   */
  public void setGeneratedName() {
    setName(suggestedName());
    myNameChangedByUser = false;
  }

  @Override
  public String suggestedName() {
    return null;
  }

  public void setNameChangedByUser(boolean nameChangedByUser) {
    myNameChangedByUser = nameChangedByUser;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myNameChangedByUser = "true".equals(element.getAttributeValue(ATTR_NAME_CHANGED_BY_USER));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (suggestedName() != null && myNameChangedByUser) {
      element.setAttribute(ATTR_NAME_CHANGED_BY_USER, "true");
    }
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {

  }

  /**
   * Returns the text of the context menu action to start this run configuration. This can be different from the run configuration name
   * (for example, for a Java unit test method, the context menu shows just the name of the method, whereas the name of the run
   * configuration includes the class name).
   *
   * @return the name of the action.
   */
  public String getActionName() {
    String name = getName();
    return name.length() < 20 ? name : name.substring(0, 20) + "...";
  }
}
