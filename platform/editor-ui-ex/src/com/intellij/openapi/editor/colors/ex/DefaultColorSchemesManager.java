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
package com.intellij.openapi.editor.colors.ex;

import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
  name = "DefaultColorSchemesManager",
  defaultStateAsResource = true,
  storages = @Storage(value = "other.xml", roamingType = RoamingType.DISABLED)
)
public class DefaultColorSchemesManager implements PersistentStateComponent<Element> {
  private final List<DefaultColorsScheme> mySchemes;
  @NonNls private static final String SCHEME_ELEMENT = "scheme";

  public DefaultColorSchemesManager() {
    mySchemes = new ArrayList<DefaultColorsScheme>();
  }

  public static DefaultColorSchemesManager getInstance() {
    return ServiceManager.getService(DefaultColorSchemesManager.class);
  }

  @Nullable
  @Override
  public Element getState() {
    return null;
  }

  @Override
  public void loadState(Element state) {
    int index = 0;
    int count = mySchemes.size();
    for (Element schemeElement : state.getChildren(SCHEME_ELEMENT)) {
      if (index < count) {
        // update a scheme that is already loaded
        DefaultColorsScheme oldScheme = mySchemes.get(index++);
        oldScheme.readExternal(schemeElement);
      }
      else {
        assert index == 0 : "config file modified: scheme added";
        DefaultColorsScheme newScheme = new DefaultColorsScheme();
        newScheme.readExternal(schemeElement);
        mySchemes.add(newScheme);
      }
    }
    assert index == count : "config file modified: scheme removed";
    while (index < count--) {
      mySchemes.remove(index);
    }
  }

  @NotNull
  public DefaultColorsScheme[] getAllSchemes() {
    return mySchemes.toArray(new DefaultColorsScheme[mySchemes.size()]);
  }

  @NotNull
  public DefaultColorsScheme getFirstScheme() {
    return mySchemes.get(0);
  }

  @Nullable
  public EditorColorsScheme getScheme(String name) {
    for (DefaultColorsScheme scheme : mySchemes) {
      if (name.equals(scheme.getName())) return scheme;
    }
    return null;
  }
}
