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

package com.intellij.openapi.roots;

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nullable;

public class LanguageLevelModuleExtensionImpl extends ModuleExtension implements LanguageLevelModuleExtension,
                                                                                 PersistentStateComponentWithModificationTracker<LanguageLevelState> {
  private static final Logger LOG = Logger.getInstance(LanguageLevelModuleExtensionImpl.class);

  private Module myModule;
  private final boolean myWritable;

  private final LanguageLevelModuleExtensionImpl mySource;

  private LanguageLevelState myState = new LanguageLevelState();

  @Override
  public long getStateModificationCount() {
    return myState.getModificationCount();
  }

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
    mySource = source;
    // setter must be used instead of creating new state with constructor param because in any case default language level for module is null (i.e. project language level)
    myState.setLanguageLevel(source.getLanguageLevel());
  }

  @Override
  public void setLanguageLevel(final LanguageLevel languageLevel) {
    LOG.assertTrue(myWritable, "Writable model can be retrieved from writable ModifiableRootModel");
    myState.setLanguageLevel(languageLevel);
  }

  @Nullable
  @Override
  public LanguageLevelState getState() {
    return myState;
  }

  @Override
  public void loadState(LanguageLevelState state) {
    myState = state;
  }

  @Override
  public ModuleExtension getModifiableModel(final boolean writable) {
    return new LanguageLevelModuleExtensionImpl(this, writable);
  }

  @Override
  public void commit() {
    if (isChanged()) {
      mySource.myState.setLanguageLevel(myState.getLanguageLevel());
      LanguageLevelProjectExtension.getInstance(myModule.getProject()).languageLevelsChanged();
    }
  }

  @Override
  public boolean isChanged() {
    return mySource != null && !mySource.myState.equals(myState);
  }

  @Override
  public void dispose() {
    myModule = null;
    myState = null;
  }

  @Nullable
  @Override
  public LanguageLevel getLanguageLevel() {
    return myState.getLanguageLevel();
  }
}
