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

/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageLevelModuleExtensionImpl  extends ModuleExtension<LanguageLevelModuleExtensionImpl> implements LanguageLevelModuleExtension {
  @NonNls private static final String LANGUAGE_LEVEL_ELEMENT_NAME = "LANGUAGE_LEVEL";
  private Module myModule;
  private final boolean myWritable;
  private static final Logger LOG = Logger.getInstance("#" + LanguageLevelModuleExtensionImpl.class.getName());

  private LanguageLevel myLanguageLevel;
  private final LanguageLevelModuleExtensionImpl mySource;

  public static LanguageLevelModuleExtensionImpl getInstance(final Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtensionImpl.class);
  }
  
  public LanguageLevelModuleExtensionImpl(Module module) {
    myModule = module;
    mySource = null;
    myWritable = false;
  }

  public LanguageLevelModuleExtensionImpl(final LanguageLevelModuleExtensionImpl source, boolean writable) {
    myWritable = writable;
    myModule = source.myModule;
    myLanguageLevel = source.myLanguageLevel;
    mySource = source;
  }

  @Override
  public void setLanguageLevel(final LanguageLevel languageLevel) {
    LOG.assertTrue(myWritable, "Writable model can be retrieved from writable ModifiableRootModel");
    myLanguageLevel = languageLevel;
  }

  @Override
  public void readExternal(@NotNull Element element) {
    final String languageLevel = element.getAttributeValue(LANGUAGE_LEVEL_ELEMENT_NAME);
    if (languageLevel != null) {
      try {
        myLanguageLevel = LanguageLevel.valueOf(languageLevel);
      }
      catch (IllegalArgumentException e) {
        //bad value was stored
      }
    }
    else {
      myLanguageLevel = null;
    }
  }

  @Override
  public void writeExternal(final Element element) {
    if (myLanguageLevel != null) {
      element.setAttribute(LANGUAGE_LEVEL_ELEMENT_NAME, myLanguageLevel.toString());
    }
  }

  @Override
  public ModuleExtension getModifiableModel(final boolean writable) {
    return new LanguageLevelModuleExtensionImpl(this, writable);
  }

  @Override
  public void commit() {
    if (mySource != null && mySource.myLanguageLevel != myLanguageLevel) {
      mySource.myLanguageLevel = myLanguageLevel;
      LanguageLevelProjectExtension.getInstance(myModule.getProject()).languageLevelsChanged();
    }
  }

  @Override
  public boolean isChanged() {
    return mySource != null && mySource.myLanguageLevel != myLanguageLevel;
  }

  @Override
  public void dispose() {
    myModule = null;
    myLanguageLevel = null;
  }

  @Nullable
  @Override
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }
}
