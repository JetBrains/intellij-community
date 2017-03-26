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

import com.intellij.application.options.schemes.SchemeNameGenerator;
import com.intellij.configurationStore.LazySchemeProcessor;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class CodeStyleSchemesImpl extends CodeStyleSchemes {

  @NonNls
  static final String CODE_STYLES_DIR_PATH = "codestyles";

  protected final SchemeManager<CodeStyleScheme> mySchemeManager;

  public CodeStyleSchemesImpl(@NotNull SchemeManagerFactory schemeManagerFactory) {
    mySchemeManager = schemeManagerFactory.create(CODE_STYLES_DIR_PATH, new LazySchemeProcessor<CodeStyleScheme, CodeStyleSchemeImpl>() {
      @NotNull
      @Override
      public CodeStyleSchemeImpl createScheme(@NotNull SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder,
                                              @NotNull String name,
                                              @NotNull Function<String, String> attributeProvider,
                                              boolean isBundled) {
        return new CodeStyleSchemeImpl(attributeProvider.apply("name"), attributeProvider.apply("parent"), dataHolder);
      }
    });

    mySchemeManager.loadSchemes();
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
    return new CodeStyleSchemeImpl(
      SchemeNameGenerator.getUniqueName(preferredName, parentScheme, name -> mySchemeManager.findSchemeByName(name) != null), 
      false,
      parentScheme);
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
    CodeStyleScheme defaultScheme = mySchemeManager.findSchemeByName(CodeStyleSchemeImpl.DEFAULT_SCHEME_NAME);
    if (defaultScheme == null) {
      defaultScheme = new CodeStyleSchemeImpl(CodeStyleSchemeImpl.DEFAULT_SCHEME_NAME, true, null);
      addScheme(defaultScheme);
    }
    return defaultScheme;
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
