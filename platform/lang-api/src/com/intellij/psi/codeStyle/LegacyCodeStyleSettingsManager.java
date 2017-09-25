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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jdom.Element;

/**
 * Supports old code style settings (before version 2017.3). The settings are returned as is without any
 * changes.
 * @author Rustam Vishnyakov
 */
@State(name = "ProjectCodeStyleSettingsManager", storages = @Storage("codeStyleSettings.xml"))
public class LegacyCodeStyleSettingsManager implements PersistentStateComponent<Element> {
  private Element myState;
  
  @Override
  public Element getState() {
    return myState;
  }

  @Override
  public void loadState(Element state) {
    myState = state;
  }
}
