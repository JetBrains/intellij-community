/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

public final class RunnerSettings<T extends JDOMExternalizable> implements JDOMExternalizable {
  private final T myData;
  private final RunProfile myConfig;

  public RunnerSettings(T data, RunProfile config) {
    myData = data;
    myConfig = config;
  }

  public T getData() {
    return myData;
  }

  public RunProfile getRunProfile() {
    return myConfig;
  }

  public void readExternal(Element element) throws InvalidDataException {
    if (myData != null) {
      myData.readExternal(element);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myData != null) {
      myData.writeExternal(element);
    }
  }
}