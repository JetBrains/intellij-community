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

import com.intellij.configurationStore.LazySchemeProcessor;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.options.SchemeState;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class CodeStyleSchemesImpl extends CodeStyleSchemes {
  protected static final String DEFAULT_SCHEME_NAME = "Default";

  @NonNls
  static final String CODE_STYLES_DIR_PATH = "codestyles";

  protected final SchemeManager<CodeStyleScheme> mySchemeManager;

  public CodeStyleSchemesImpl(@NotNull SchemeManagerFactory schemeManagerFactory) {
    mySchemeManager = schemeManagerFactory.create(CODE_STYLES_DIR_PATH, new LazySchemeProcessor<CodeStyleScheme, CodeStyleSchemeImpl>() {
      @NotNull
      @Override
      public CodeStyleSchemeImpl createScheme(@NotNull SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder, @NotNull String name, @NotNull Function<String, String> attributeProvider) {
        return new CodeStyleSchemeImpl(attributeProvider.apply("name"), attributeProvider.apply("parent"), dataHolder);
      }

      @NotNull
      @Override
      public SchemeState getState(@NotNull CodeStyleScheme scheme) {
        if (scheme.isDefault() || !(scheme instanceof CodeStyleSchemeImpl)) {
          return SchemeState.NON_PERSISTENT;
        }
        else {
          return ((CodeStyleSchemeImpl)scheme).isInitialized() ? SchemeState.POSSIBLY_CHANGED : SchemeState.UNCHANGED;
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
  public void deleteScheme(@NotNull CodeStyleScheme scheme) {
    if (scheme.isDefault()) {
      throw new IllegalArgumentException("Unable to delete default scheme!");
    }

    CodeStyleSchemeImpl currentScheme = (CodeStyleSchemeImpl)getCurrentScheme();
    if (currentScheme == scheme) {
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
  public static SchemeManager<CodeStyleScheme> getSchemeManager() {
    return ((CodeStyleSchemesImpl)CodeStyleSchemes.getInstance()).mySchemeManager;
  }
}
