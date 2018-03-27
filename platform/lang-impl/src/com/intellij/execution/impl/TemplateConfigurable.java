/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.RunnerAndConfigurationSettings;
import org.jdom.Element;

/**
* @author Dmitry Avdeev
*/
class TemplateConfigurable extends BaseRCSettingsConfigurable {
  private final RunnerAndConfigurationSettings myTemplate;

  public TemplateConfigurable(RunnerAndConfigurationSettings template) {
    super(new ConfigurationSettingsEditorWrapper(template), template);
    myTemplate = template;
  }

  @Override
  void patchElementsIfNeed(Element originalElement, Element snapshotElement) {
    snapshotElement.setAttribute(RunnerAndConfigurationSettingsImplKt.getTEMPLATE_FLAG_ATTRIBUTE(), "true");
  }

  @Override
  public String getDisplayName() {
    return myTemplate.getConfiguration().getName();
  }

  @Override
  public String getHelpTopic() {
    return null;
  }
}
