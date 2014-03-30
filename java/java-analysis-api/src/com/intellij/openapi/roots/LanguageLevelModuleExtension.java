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

/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class LanguageLevelModuleExtension extends ModuleExtension<LanguageLevelModuleExtension> {
  @NonNls private static final String LANGUAGE_LEVEL_ELEMENT_NAME = "LANGUAGE_LEVEL";
  private Module myModule;
  private final boolean myWritable;
  private static final Logger LOG = Logger.getInstance("#" + LanguageLevelModuleExtension.class.getName());

  public static LanguageLevelModuleExtension getInstance(final Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
  }

  private LanguageLevel myLanguageLevel;
  private final LanguageLevelModuleExtension mySource;

  public LanguageLevelModuleExtension(Module module) {
    myModule = module;
    mySource = null;
    myWritable = false;
  }

  public LanguageLevelModuleExtension(final LanguageLevelModuleExtension source, boolean writable) {
    myWritable = writable;
    myModule = source.myModule;
    myLanguageLevel = source.myLanguageLevel;
    mySource = source;
  }

  public void setLanguageLevel(final LanguageLevel languageLevel) {
    LOG.assertTrue(myWritable, "Writable model can be retrieved from writable ModifiableRootModel");
    myLanguageLevel = languageLevel;
  }

  @Nullable
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public void readExternal(final Element element) throws InvalidDataException {
    final String languageLevel = element.getAttributeValue(LANGUAGE_LEVEL_ELEMENT_NAME);
    if (languageLevel != null) {
      try {
        myLanguageLevel = LanguageLevel.valueOf(languageLevel);
      }
      catch (IllegalArgumentException e) {
        //bad value was stored
      }
    }
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    if (myLanguageLevel != null) {
      element.setAttribute(LANGUAGE_LEVEL_ELEMENT_NAME, myLanguageLevel.toString());
    }
  }

  @Override
  public ModuleExtension getModifiableModel(final boolean writable) {
    return new LanguageLevelModuleExtension(this, writable);
  }

  @Override
  public void commit() {
    if (mySource != null && mySource.myLanguageLevel != myLanguageLevel) {
      if (myModule.isLoaded()) { //do not reload project for non-committed modules: j2me|project imports
        if (mySource.myLanguageLevel != myLanguageLevel) {
          final LanguageLevelProjectExtension languageLevelProjectExtension =
            LanguageLevelProjectExtension.getInstance(myModule.getProject());
          final LanguageLevel projectLanguageLevel = languageLevelProjectExtension.getLanguageLevel();
          final boolean explicit2ImplicitProjectLevel = myLanguageLevel == null && mySource.myLanguageLevel == projectLanguageLevel;
          final boolean implicit2ExplicitProjectLevel = mySource.myLanguageLevel == null && myLanguageLevel == projectLanguageLevel;
          if (!(explicit2ImplicitProjectLevel || implicit2ExplicitProjectLevel)) {
            languageLevelProjectExtension.reloadProjectOnLanguageLevelChange(myLanguageLevel == null ? projectLanguageLevel : myLanguageLevel, true);
          }
        }
      }
      mySource.myLanguageLevel = myLanguageLevel;
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
}
