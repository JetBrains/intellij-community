// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots;

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
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
    if (myState.getLanguageLevel() == languageLevel) return;

    LOG.assertTrue(myWritable, "Writable model can be retrieved from writable ModifiableRootModel");
    myState.setLanguageLevel(languageLevel);
  }

  @Nullable
  @Override
  public LanguageLevelState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull LanguageLevelState state) {
    myState = state;
  }

  @NotNull
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
