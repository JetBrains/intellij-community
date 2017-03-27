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
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.NAME_ATTR;

@State(
  name = "DefaultColorSchemesManager",
  defaultStateAsResource = true,
  storages = @Storage(value = "other.xml", roamingType = RoamingType.DISABLED)
)
public class DefaultColorSchemesManager implements PersistentStateComponent<Element> {
  private static final String SCHEME_ELEMENT = "scheme";

  private volatile List<DefaultColorsScheme> mySchemes = Collections.emptyList();

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
    List<DefaultColorsScheme> schemes = new ArrayList<>();
    for (Element schemeElement : state.getChildren(SCHEME_ELEMENT)) {
      boolean isUpdated = false;
      Attribute nameAttr = schemeElement.getAttribute(NAME_ATTR);
      if (nameAttr != null) {
        for (DefaultColorsScheme oldScheme : mySchemes) {
          if (StringUtil.equals(nameAttr.getValue(), oldScheme.getName())) {
            oldScheme.readExternal(schemeElement);
            schemes.add(oldScheme);
            isUpdated = true;
          }
        }
      }
      if (!isUpdated) {
        DefaultColorsScheme newScheme = new DefaultColorsScheme();
        newScheme.readExternal(schemeElement);
        schemes.add(newScheme);
      }
    }
    schemes.add(EmptyColorScheme.INSTANCE);
    mySchemes = schemes;
  }

  @NotNull
  public List<DefaultColorsScheme> getAllSchemes() {
    return Collections.unmodifiableList(mySchemes);
  }

  public String[] listNames() {
    String[] names = new String[mySchemes.size()];
    for (int i = 0; i < names.length; i ++) {
      names[i] = mySchemes.get(i).getName();
    }
    return names;
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
