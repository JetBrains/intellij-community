/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public final class ConfigurationPerRunnerSettings implements JDOMExternalizable {
  private final JDOMExternalizable mySettings;
  private final String myRunnerId;

  public ConfigurationPerRunnerSettings(String runnerId, JDOMExternalizable settings) {
    mySettings = settings;
    myRunnerId = runnerId;
  }

  public String getRunnerId() {
    return myRunnerId;
  }

  public JDOMExternalizable getSettings() {
    return mySettings;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    if (mySettings != null) {
      mySettings.readExternal(element);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (mySettings != null) {
      mySettings.writeExternal(element);
    }
  }
}
