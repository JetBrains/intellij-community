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
package com.intellij.util.config;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBSplitter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link PropertiesComponent} directly.
 * @see JBSplitter
 */
@SuppressWarnings("unused")
@Deprecated
public class StorageAccessors {
  private final Storage myStorage;

  public StorageAccessors(Storage storage) {
    myStorage = storage;
  }

  @NotNull
  public static StorageAccessors createGlobal(@NotNull @NonNls String prefix) {
    Application application = ApplicationManager.getApplication();
    return new StorageAccessors(application == null ? new Storage.MapStorage() : new Storage.PropertiesComponentStorage(prefix + "."));
  }

  public float getFloat(@NonNls String id, float defaultValue) {
    String value = myStorage.get(id);
    if (value == null) return defaultValue;
    try {
      return Float.parseFloat(value);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public void setFloat(String id, float value) {
    myStorage.put(id, String.valueOf(value));
  }

  public boolean getBoolean(String id, boolean defaultValue) {
    return Boolean.valueOf(myStorage.get(id));
  }

  public void setBoolean(String id, boolean value) {
    myStorage.put(id, String.valueOf(value));
  }
}
