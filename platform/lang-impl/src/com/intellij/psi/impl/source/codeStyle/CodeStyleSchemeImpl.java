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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ExternalizableSchemeAdapter;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleSchemeImpl extends ExternalizableSchemeAdapter implements CodeStyleScheme {
  private static final Logger LOG = Logger.getInstance(CodeStyleSchemeImpl.class);

  private Element myRootElement;
  private String myParentSchemeName;
  private final boolean myIsDefault;
  private volatile CodeStyleSettings myCodeStyleSettings;

  CodeStyleSchemeImpl(@NotNull String name, String parentSchemeName, Element rootElement) {
    myName = name;
    myRootElement = rootElement;
    myIsDefault = false;
    myParentSchemeName = parentSchemeName;
  }

  public CodeStyleSchemeImpl(@NotNull String name, boolean isDefault, CodeStyleScheme parentScheme){
    myName = name;
    myIsDefault = isDefault;
    init(parentScheme, null);
  }

  void init(@NotNull SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> schemesManager) {
    LOG.assertTrue(myCodeStyleSettings == null, "Already initialized");
    init(myParentSchemeName == null ? null : schemesManager.findSchemeByName(myParentSchemeName), myRootElement);
    myParentSchemeName = null;
    myRootElement = null;
  }

  private void init(@Nullable CodeStyleScheme parentScheme, Element root) {
    if (parentScheme == null) {
      myCodeStyleSettings = new CodeStyleSettings();
    }
    else {
      CodeStyleSettings parentSettings = parentScheme.getCodeStyleSettings();
      myCodeStyleSettings = parentSettings.clone();
      while (parentSettings.getParentSettings() != null) {
        parentSettings = parentSettings.getParentSettings();
      }
      myCodeStyleSettings.setParentSettings(parentSettings);
    }
    if (root != null) {
      try {
        myCodeStyleSettings.readExternal(root);
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public CodeStyleSettings getCodeStyleSettings(){
    return myCodeStyleSettings;
  }

  public void setCodeStyleSettings(@NotNull CodeStyleSettings codeStyleSettings){
    myCodeStyleSettings = codeStyleSettings;
  }

  @Override
  public boolean isDefault() {
    return myIsDefault;
  }

  public void writeExternal(Element element) throws WriteExternalException{
    myCodeStyleSettings.writeExternal(element);
  }
}
