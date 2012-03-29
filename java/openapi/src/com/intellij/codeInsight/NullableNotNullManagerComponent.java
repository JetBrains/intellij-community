/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * User: anna
 * Date: 1/25/11
 */
@State(
    name = "NullableNotNullManager",
    storages = {@Storage( file = "$PROJECT_FILE$")}
)
public class NullableNotNullManagerComponent extends NullableNotNullManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#" + NullableNotNullManagerComponent.class.getName());

  @Override
  public Element getState() {
    final Element component = new Element("component");

    if (hasDefaultValues()) {
      return component;
    }

    try {
      DefaultJDOMExternalizer.writeExternal(this, component);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return component;
  }

  @Override
  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }
}

