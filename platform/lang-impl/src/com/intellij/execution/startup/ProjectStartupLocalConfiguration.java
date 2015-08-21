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
package com.intellij.execution.startup;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
@State(
  name = "ProjectStartupLocalConfiguration",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class ProjectStartupLocalConfiguration extends ProjectStartupConfigurationBase {
  private final static String SHARED = "shared";
  private boolean myIsShared;

  @Nullable
  @Override
  public Element getState() {
    Element state = super.getState();
    if (state == null) {
      if (! myIsShared) return null;
      state = new Element(TOP_ELEMENT);
    }
    state.setAttribute(SHARED, String.valueOf(myIsShared));
    return state;
  }

  @Override
  public void loadState(Element state) {
    super.loadState(state);
    if (state != null) {
      myIsShared = "true".equals(state.getAttributeValue(SHARED));
    }
  }

  public void local() {
    myIsShared = false;
  }

  public void shared() {
    myIsShared = true;
  }

  public boolean isShared() {
    return myIsShared;
  }
}
