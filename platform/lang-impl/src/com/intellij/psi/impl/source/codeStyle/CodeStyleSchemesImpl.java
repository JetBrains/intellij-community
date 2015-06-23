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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class CodeStyleSchemesImpl extends CodeStyleSchemes {
  protected static final String DEFAULT_SCHEME_NAME = "Default";

  @NonNls
  static final String CODE_STYLES_DIR_PATH = StoragePathMacros.ROOT_CONFIG + "/codestyles";

  public String CURRENT_SCHEME_NAME = DEFAULT_SCHEME_NAME;

  protected final SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> mySchemesManager;

  public CodeStyleSchemesImpl(@NotNull SchemesManagerFactory schemesManagerFactory) {
    mySchemesManager = schemesManagerFactory.createSchemesManager(CODE_STYLES_DIR_PATH, new BaseSchemeProcessor<CodeStyleSchemeImpl>() {
      @NotNull
      @Override
      public CodeStyleSchemeImpl readScheme(@NotNull Element element) {
        return new CodeStyleSchemeImpl(element.getAttributeValue("name"), element.getAttributeValue("parent"), element);
      }

      @Override
      public Element writeScheme(@NotNull CodeStyleSchemeImpl scheme) throws WriteExternalException {
        Element newElement = new Element("code_scheme");
        newElement.setAttribute("name", scheme.getName());
        scheme.writeExternal(newElement);
        return newElement;
      }

      @NotNull
      @Override
      public State getState(@NotNull CodeStyleSchemeImpl scheme) {
        return scheme.isDefault() ? State.NON_PERSISTENT : State.POSSIBLY_CHANGED;
      }

      @Override
      public void initScheme(@NotNull CodeStyleSchemeImpl scheme) {
        scheme.init(CodeStyleSchemesImpl.this);
      }
    }, RoamingType.PER_USER);

    mySchemesManager.loadSchemes();
    addScheme(new CodeStyleSchemeImpl(DEFAULT_SCHEME_NAME, true, null));
    setCurrentScheme(getDefaultScheme());
  }

  @Override
  public CodeStyleScheme[] getSchemes() {
    Collection<CodeStyleScheme> schemes = mySchemesManager.getAllSchemes();
    return schemes.toArray(new CodeStyleScheme[schemes.size()]);
  }

  @Override
  public CodeStyleScheme getCurrentScheme() {
    return mySchemesManager.getCurrentScheme();
  }

  @Override
  public void setCurrentScheme(CodeStyleScheme scheme) {
    String schemeName = scheme == null ? null : scheme.getName();
    mySchemesManager.setCurrentSchemeName(schemeName);
    CURRENT_SCHEME_NAME = schemeName;
  }

  @SuppressWarnings("ForLoopThatDoesntUseLoopVariable")
  @Override
  public CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme) {
    String name;
    if (preferredName == null) {
      if (parentScheme == null) throw new IllegalArgumentException("parentScheme must not be null");
      // Generate using parent name
      name = null;
      for (int i = 1; name == null; i++) {
        String currName = parentScheme.getName() + " (" + i + ")";
        if (findSchemeByName(currName) == null) {
          name = currName;
        }
      }
    }
    else {
      name = null;
      for (int i = 0; name == null; i++) {
        String currName = i == 0 ? preferredName : preferredName + " (" + i + ")";
        if (findSchemeByName(currName) == null) {
          name = currName;
        }
      }
    }
    return new CodeStyleSchemeImpl(name, false, parentScheme);
  }

  @Override
  public void deleteScheme(CodeStyleScheme scheme) {
    if (scheme.isDefault()) {
      throw new IllegalArgumentException("Unable to delete default scheme!");
    }
    CodeStyleSchemeImpl currScheme = (CodeStyleSchemeImpl)getCurrentScheme();
    if (currScheme == scheme) {
      CodeStyleScheme newCurrentScheme = getDefaultScheme();
      if (newCurrentScheme == null) {
        throw new IllegalStateException("Unable to load default scheme!");
      }
      setCurrentScheme(newCurrentScheme);
    }
    mySchemesManager.removeScheme(scheme);
  }

  @Override
  public void setSchemes(@NotNull List<CodeStyleScheme> schemes) {
    mySchemesManager.setSchemes(schemes);
  }

  @Override
  public CodeStyleScheme getDefaultScheme() {
    return findSchemeByName(DEFAULT_SCHEME_NAME);
  }

  @Nullable
  @Override
  public CodeStyleScheme findSchemeByName(@NotNull String name) {
    return mySchemesManager.findSchemeByName(name);
  }

  @Override
  public void addScheme(@NotNull CodeStyleScheme scheme) {
    mySchemesManager.addScheme(scheme);
  }
}
