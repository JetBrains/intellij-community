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

package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FrameworkSupportConfigurable implements Disposable {
  private final EventDispatcher<FrameworkSupportConfigurableListener> myDispatcher = EventDispatcher.create(FrameworkSupportConfigurableListener.class);

  @Nullable
  public abstract JComponent getComponent();

  public abstract void addSupport(@NotNull Module module, @NotNull ModifiableRootModel model, final @Nullable Library library);

  @SuppressWarnings({"ConstantConditions"})
  public FrameworkVersion getSelectedVersion() {
    return null;
  }

  public void onFrameworkSelectionChanged(boolean selected) {
  }

  public void addListener(@NotNull FrameworkSupportConfigurableListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(@NotNull FrameworkSupportConfigurableListener listener) {
    myDispatcher.removeListener(listener);
  }

  protected void fireFrameworkVersionChanged() {
    myDispatcher.getMulticaster().frameworkVersionChanged();
  }

  @Override
  public void dispose() {
  }
}
