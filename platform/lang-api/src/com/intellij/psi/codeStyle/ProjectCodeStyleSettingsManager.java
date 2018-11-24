/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;


@State(name = "ProjectCodeStyleSettingsManager", storages = @Storage("codeStyleSettings.xml"))
public class ProjectCodeStyleSettingsManager extends CodeStyleSettingsManager{
  private static final Logger LOG = Logger.getInstance("#" + ProjectCodeStyleSettingsManager.class);

  private volatile boolean myIsLoaded;
  private final static Object LEGACY_SETTINGS_IMPORT_LOCK = new Object();

  void importLegacySettings(@NotNull Project project) {
    if (!myIsLoaded) {
      synchronized (LEGACY_SETTINGS_IMPORT_LOCK) {
        if (!myIsLoaded) {
          LegacyCodeStyleSettingsManager legacySettingsManager = ServiceManager.getService(project, LegacyCodeStyleSettingsManager.class);
          if (legacySettingsManager != null && legacySettingsManager.getState() != null) {
            loadState(legacySettingsManager.getState());
            LOG.info("Imported old project code style settings.");
          }
        }
      }
    }
  }

  @Override
  public void loadState(Element state) {
    super.loadState(state);
    myIsLoaded = true;
  }
}
