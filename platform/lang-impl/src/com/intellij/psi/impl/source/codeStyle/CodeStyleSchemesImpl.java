/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.SchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;

/**
 * @author MYakovlev
 *         Date: Jul 16, 2002
 */
public abstract class CodeStyleSchemesImpl extends CodeStyleSchemes implements ExportableComponent {
  @NonNls public static final String DEFAULT_SCHEME_NAME = "Default";

  public String CURRENT_SCHEME_NAME = DEFAULT_SCHEME_NAME;
  private boolean myIsInitialized = false;
  @NonNls static final String CODESTYLES_DIRECTORY = "codestyles";

  private final SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> mySchemesManager;
  @NonNls private static final String FILE_SPEC = "$ROOT_CONFIG$/" + CODESTYLES_DIRECTORY;

  public CodeStyleSchemesImpl(SchemesManagerFactory schemesManagerFactory) {
    SchemeProcessor<CodeStyleSchemeImpl> processor = new BaseSchemeProcessor<CodeStyleSchemeImpl>() {
      @Override
      public CodeStyleSchemeImpl readScheme(@NotNull final Document schemeContent) throws IOException, JDOMException, InvalidDataException {
        return CodeStyleSchemeImpl.readScheme(schemeContent);
      }

      @Override
      public Document writeScheme(@NotNull final CodeStyleSchemeImpl scheme) throws WriteExternalException {
        return scheme.saveToDocument();
      }

      @Override
      public boolean shouldBeSaved(@NotNull final CodeStyleSchemeImpl scheme) {
        return !scheme.isDefault();
      }

      @Override
      public void initScheme(@NotNull final CodeStyleSchemeImpl scheme) {
        scheme.init(CodeStyleSchemesImpl.this);
      }
    };

    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, processor, RoamingType.PER_USER);

    init();
    addScheme(new CodeStyleSchemeImpl(DEFAULT_SCHEME_NAME, true, null));
    setCurrentScheme(getDefaultScheme());
  }

  @Override
  public CodeStyleScheme[] getSchemes() {
    final Collection<CodeStyleScheme> schemes = mySchemesManager.getAllSchemes();
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

  @Override
  public CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme) {
    String name;
    if (preferredName == null) {
      // Generate using parent name
      name = null;
      for (int i = 1; name == null; i++) {
        String currName = parentScheme.getName() + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    else {
      name = null;
      for (int i = 0; name == null; i++) {
        String currName = i == 0 ? preferredName : preferredName + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
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
  public CodeStyleScheme getDefaultScheme() {
    return findSchemeByName(DEFAULT_SCHEME_NAME);
  }

  @Override
  public CodeStyleScheme findSchemeByName(String name) {
    return mySchemesManager.findSchemeByName(name);
  }

  @Override
  public void addScheme(CodeStyleScheme scheme) {
    mySchemesManager.addNewScheme(scheme, true);
  }

  protected void removeScheme(CodeStyleScheme scheme) {
    mySchemesManager.removeScheme(scheme);
  }

  protected void init() {
    if (myIsInitialized) return;
    myIsInitialized = true;
    mySchemesManager.loadSchemes();
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return PsiBundle.message("codestyle.export.display.name");
  }

  public SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> getSchemesManager() {
    return mySchemesManager;
  }


}
