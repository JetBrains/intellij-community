// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ResourceUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.NAME_ATTR;

@State(
  name = "DefaultColorSchemesManager",
  defaultStateAsResource = true,
  storages = @Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)
)
@Service
@ApiStatus.Internal
public final class DefaultColorSchemesManager {
  private static final String SCHEME_ELEMENT = "scheme";

  private volatile List<DefaultColorsScheme> mySchemes = Collections.emptyList();

  public static DefaultColorSchemesManager getInstance() {
    return ApplicationManager.getApplication().getService(DefaultColorSchemesManager.class);
  }

  public DefaultColorSchemesManager() {
    reload();
  }

  public void reload() {
    try {
      loadState(JDOMUtil.load(ResourceUtil.getResourceAsBytes("DefaultColorSchemesManager.xml",
                                                              DefaultColorSchemesManager.class.getClassLoader())));
    }
    catch (JDOMException | IOException e) {
      ExceptionUtil.rethrow(e);
      mySchemes = Collections.emptyList();
    }
  }

  // public for Upsource
  public void loadState(@NotNull Element state) {
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
    mySchemes = Collections.unmodifiableList(schemes);
  }

  @NotNull
  public List<DefaultColorsScheme> getAllSchemes() {
    return mySchemes;
  }

  @NotNull
  public List<@NonNls String> listNames() {
    String[] names = new String[mySchemes.size()];
    for (int i = 0; i < names.length; i ++) {
      names[i] = mySchemes.get(i).getName();
    }
    return Arrays.asList(names);
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
