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
package com.intellij.usageView;

import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class UsageTreeColorsScheme implements NamedComponent, JDOMExternalizable{
  private EditorColorsScheme myColorsScheme;
  private final EditorColorsManager myEditorColorsManager;
  @Deprecated
  @NonNls public static final String DEFAULT_SCHEME_NAME = EditorColorsManager.DEFAULT_SCHEME_NAME; // it is API, let's do not break it and do not inline constant

  public UsageTreeColorsScheme(EditorColorsManager editorColorsManager) {
    myEditorColorsManager = editorColorsManager;
  }

  public static UsageTreeColorsScheme getInstance() {
    return ServiceManager.getService(UsageTreeColorsScheme.class);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "FindViewColorsScheme";
  }

  public EditorColorsScheme getScheme() {
    return myColorsScheme;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    if (myColorsScheme == null){
      Color color = UIUtil.getTreeTextBackground();
      if (color != null && ColorUtil.isDark(color)) {
        myColorsScheme = (EditorColorsScheme)myEditorColorsManager.getGlobalScheme().clone();
      }
      else {
        myColorsScheme = (EditorColorsScheme)myEditorColorsManager.getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME).clone();
      }
    }
    myColorsScheme.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }
}
