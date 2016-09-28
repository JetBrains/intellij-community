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

import com.intellij.openapi.options.LazySchemeProcessor;
import com.intellij.openapi.options.SchemeDataHolder;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class CodeStyleSchemesImpl extends CodeStyleSchemes {
  protected static final String DEFAULT_SCHEME_NAME = "Default";

  @NonNls
  static final String CODE_STYLES_DIR_PATH = "codestyles";

  protected final SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> mySchemeManager;

  public CodeStyleSchemesImpl(@NotNull SchemesManagerFactory schemesManagerFactory) {
    mySchemeManager = schemesManagerFactory.create(CODE_STYLES_DIR_PATH, new LazySchemeProcessor<CodeStyleSchemeImpl>() {
      @NotNull
      @Override
      public CodeStyleSchemeImpl createScheme(@NotNull SchemeDataHolder dataHolder, @NotNull Function<String, String> attributeProvider, boolean duringLoad) {
        return new CodeStyleSchemeImpl(attributeProvider.apply("name"), attributeProvider.apply("parent"), dataHolder);
      }

      @Override
      public Element writeScheme(@NotNull CodeStyleSchemeImpl scheme) throws WriteExternalException {
        return scheme.writeScheme();
      }

      @NotNull
      @Override
      public State getState(@NotNull CodeStyleSchemeImpl scheme) {
        if (scheme.isDefault()) {
          return State.NON_PERSISTENT;
        }
        else {
          return scheme.isInitialized() ? State.POSSIBLY_CHANGED : State.UNCHANGED;
        }
      }
    });

    mySchemeManager.loadSchemes();
    addScheme(new CodeStyleSchemeImpl(DEFAULT_SCHEME_NAME, true, null));
    setCurrentScheme(getDefaultScheme());
  }

  @Override
  public CodeStyleScheme getCurrentScheme() {
    return mySchemeManager.getCurrentScheme();
  }

  @Override
  public void setCurrentScheme(CodeStyleScheme scheme) {
    mySchemeManager.setCurrent(scheme);
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
        if (mySchemeManager.findSchemeByName(currName) == null) {
          name = currName;
        }
      }
    }
    else {
      name = null;
      for (int i = 0; name == null; i++) {
        String currName = i == 0 ? preferredName : preferredName + " (" + i + ")";
        if (mySchemeManager.findSchemeByName(currName) == null) {
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
    mySchemeManager.removeScheme(scheme);
  }

  @Override
  public CodeStyleScheme getDefaultScheme() {
    return mySchemeManager.findSchemeByName(DEFAULT_SCHEME_NAME);
  }

  @Nullable
  @Override
  public CodeStyleScheme findSchemeByName(@NotNull String name) {
    return mySchemeManager.findSchemeByName(name);
  }

  @Override
  public void addScheme(@NotNull CodeStyleScheme scheme) {
    mySchemeManager.addScheme(scheme);
  }

  @NotNull
  public static SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> getSchemeManager() {
    return ((CodeStyleSchemesImpl)CodeStyleSchemes.getInstance()).mySchemeManager;
  }
}
