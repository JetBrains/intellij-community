/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.configurationStore.SerializableScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ExternalizableSchemeAdapter;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleSchemeImpl extends ExternalizableSchemeAdapter implements CodeStyleScheme, SerializableScheme {
  private static final Logger LOG = Logger.getInstance(CodeStyleSchemeImpl.class);

  private SchemeDataHolder<? super CodeStyleSchemeImpl> myDataHolder;
  private String myParentSchemeName;
  private final boolean myIsDefault;
  private volatile CodeStyleSettings myCodeStyleSettings;

  private final Object lock = new Object();

  CodeStyleSchemeImpl(@NotNull String name, String parentSchemeName, @NotNull SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder) {
    setName(name);
    myDataHolder = dataHolder;
    myIsDefault = DEFAULT_SCHEME_NAME.equals(name);
    myParentSchemeName = parentSchemeName;
  }

  public CodeStyleSchemeImpl(@NotNull String name, boolean isDefault, CodeStyleScheme parentScheme) {
    setName(name);
    myIsDefault = isDefault;
    init(parentScheme, null);
  }

  @NotNull
  private CodeStyleSettings init(@Nullable CodeStyleScheme parentScheme, @Nullable Element root) {
    final CodeStyleSettings settings;
    if (parentScheme == null) {
      settings = new CodeStyleSettings();
    }
    else {
      CodeStyleSettings parentSettings = parentScheme.getCodeStyleSettings();
      settings = parentSettings.clone();
      while (parentSettings.getParentSettings() != null) {
        parentSettings = parentSettings.getParentSettings();
      }
      settings.setParentSettings(parentSettings);
    }

    if (root != null) {
      try {
        settings.readExternal(root);
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }

    myCodeStyleSettings = settings;
    return settings;
  }

  @Override
  @NotNull
  public CodeStyleSettings getCodeStyleSettings() {
    CodeStyleSettings settings = myCodeStyleSettings;
    if (settings != null) {
      return settings;
    }

    synchronized (lock) {
      SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder = myDataHolder;
      if (dataHolder == null) {
        return myCodeStyleSettings;
      }

      myDataHolder = null;
      settings = init(myParentSchemeName == null ? null : CodeStyleSchemesImpl.getSchemeManager().findSchemeByName(myParentSchemeName), dataHolder.read());
      dataHolder.updateDigest(this);
      myParentSchemeName = null;
    }
    return settings;
  }

  public void setCodeStyleSettings(@NotNull CodeStyleSettings codeStyleSettings) {
    myCodeStyleSettings = codeStyleSettings;
    synchronized (lock) {
      myParentSchemeName = null;
      myDataHolder = null;
    }
  }

  @Override
  public boolean isDefault() {
    return myIsDefault;
  }

  @Nullable
  @Override
  public SchemeState getSchemeState() {
    synchronized (lock) {
      return myDataHolder == null ? SchemeState.POSSIBLY_CHANGED : SchemeState.UNCHANGED;
    }
  }

  @Override
  @NotNull
  public Element writeScheme() {
    SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder;
    synchronized (lock) {
      dataHolder = myDataHolder;
    }

    if (dataHolder == null) {
      Element newElement = new Element(CODE_STYLE_TAG_NAME);
      newElement.setAttribute(CODE_STYLE_NAME_ATTR, getName());
      myCodeStyleSettings.writeExternal(newElement);
      return newElement;
    }
    else {
      return dataHolder.read();
    }
  }
}
