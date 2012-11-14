/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.addSupport;

import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FrameworkSupportInModuleConfigurable implements Disposable {
  @Nullable
  public abstract JComponent createComponent();

  public abstract void addSupport(@NotNull Module module, @NotNull ModifiableRootModel rootModel,
                                  @NotNull ModifiableModelsProvider modifiableModelsProvider);

  @Nullable
  public CustomLibraryDescription createLibraryDescription() {
    return null;
  }

  @NotNull
  public FrameworkLibraryVersionFilter getLibraryVersionFilter() {
    return FrameworkLibraryVersionFilter.ALL;
  }

  public void onFrameworkSelectionChanged(boolean selected) {
  }

  public boolean isOnlyLibraryAdded() {
    return false;
  }

  public boolean isVisible() {
    return true;
  }

  @Override
  public void dispose() {
  }
}
