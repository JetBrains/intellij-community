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
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;

/**
 * This class provides 'smart' isModified() behavior: it compares original settings with current snapshot by their XML 'externalized' presentations
 */
abstract class BaseRCSettingsConfigurable extends SettingsEditorConfigurable<RunnerAndConfigurationSettings> {
  BaseRCSettingsConfigurable(SettingsEditor<RunnerAndConfigurationSettings> editor, RunnerAndConfigurationSettings settings) {
    super(editor, settings);
  }

  @Override
  public boolean isModified() {
    try {
      RunnerAndConfigurationSettings original = getSettings();
      RunnerAndConfigurationSettings snapshot = getEditor().getSnapshot();

      final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(original.getConfiguration().getProject());
      if (!runManager.hasSettings(original)) {
        return true;
      }
      if (!super.isModified()) {
        return false;
      }
      if (isSnapshotSpecificallyModified(runManager, original, snapshot) || !runManager.getBeforeRunTasks(original.getConfiguration()).equals(runManager.getBeforeRunTasks(snapshot.getConfiguration()))) {
        return true;
      }
      if (original instanceof JDOMExternalizable && snapshot instanceof JDOMExternalizable) {
        applySnapshotToComparison(original, snapshot);

        Element originalElement = new Element("config");
        Element snapshotElement = new Element("config");
        ((JDOMExternalizable)original).writeExternal(originalElement);
        ((JDOMExternalizable)snapshot).writeExternal(snapshotElement);
        patchElementsIfNeed(originalElement, snapshotElement);
        boolean result = !JDOMUtil.areElementsEqual(originalElement, snapshotElement, true);
        if (!result) {
          super.setModified(false);
        }
        return result;
      }
    }
    catch (ConfigurationException e) {
      //ignore
    }
    return super.isModified();
  }

  void applySnapshotToComparison(RunnerAndConfigurationSettings original, RunnerAndConfigurationSettings snapshot) {}

  boolean isSnapshotSpecificallyModified(RunManagerImpl runManager,
                                         RunnerAndConfigurationSettings original,
                                         RunnerAndConfigurationSettings snapshot) {
    return false;
  }

  void patchElementsIfNeed(Element originalElement, Element snapshotElement) {}
}
