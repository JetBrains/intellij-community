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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.OptionManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.keyFMap.KeyFMap;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

final class ModuleFileData extends BaseFileConfigurableStoreImpl.BaseStorageData implements OptionManager {
  private KeyFMap options;
  private final Module myModule;

  private boolean dirty = true;

  public ModuleFileData(@NotNull String rootElementName, @NotNull Module module) {
    super(rootElementName);

    myModule = module;
    options = KeyFMap.EMPTY_MAP;
  }

  @Override
  public boolean isDirty() {
    return dirty;
  }

  private ModuleFileData(@NotNull ModuleFileData storageData) {
    super(storageData);

    myModule = storageData.myModule;
    dirty = storageData.dirty;
    options = storageData.options;
  }

  @Override
  public void load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean intern) {
    super.load(rootElement, pathMacroSubstitutor, intern);

    KeyFMap options = KeyFMap.EMPTY_MAP;
    for (Attribute attribute : rootElement.getAttributes()) {
      String name = attribute.getName();
      if (!name.equals(BaseFileConfigurableStoreImpl.VERSION_OPTION) && !StringUtil.isEmpty(name)) {
        options.plus(ModuleManagerImpl.createOptionKey(name), attribute.getValue());
      }
    }

    dirty = false;
  }

  @Override
  protected void writeOptions(@NotNull Element root, @NotNull String versionString) {
    if (!options.isEmpty()) {
      //noinspection unchecked
      for (Key<String> key : options.getKeys()) {
        String value = options.get(key);
        if (value != null) {
          root.setAttribute(key.toString(), value);
        }
      }
    }
    // need be last for compat reasons
    super.writeOptions(root, versionString);

    dirty = false;
  }

  @Override
  public StorageData clone() {
    return new ModuleFileData(this);
  }

  @Nullable
  @Override
  public Set<String> getChangedComponentNames(@NotNull StorageData newStorageData, @Nullable PathMacroSubstitutor substitutor) {
    final ModuleFileData data = (ModuleFileData)newStorageData;
    if (options != data.options) {
      return null;
    }

    return super.getChangedComponentNames(newStorageData, substitutor);
  }

  @Override
  public void setOption(@NotNull Key<String> key, @NotNull String optionValue) {
    if (optionValue.equals(options.get(key))) {
      return;
    }

    options = options.plus(key, optionValue);
    dirty = true;
  }

  @Override
  public void clearOption(@NotNull Key<String> key) {
    KeyFMap newOptions = options.minus(key);
    if (newOptions != options) {
      options = newOptions;
      dirty = true;
    }
  }

  @Override
  @Nullable
  public String getOptionValue(@NotNull Key<String> key) {
    return options.get(key);
  }
}
