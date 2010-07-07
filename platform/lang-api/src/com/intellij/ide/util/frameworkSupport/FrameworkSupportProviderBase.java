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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkSupportProviderBase extends FrameworkSupportProvider {

  protected FrameworkSupportProviderBase(final @NonNls @NotNull String id, final @NotNull String title) {
    super(id, title);
  }

  protected abstract void addSupport(@NotNull Module module, @NotNull ModifiableRootModel rootModel, FrameworkVersion version, final @Nullable Library library);

  @NotNull
  public List<FrameworkVersion> getVersions() {
    return Collections.emptyList();
  }

  public String getVersionLabelText() {
    return "Version:";
  }

  @NotNull
  public FrameworkSupportConfigurableBase createConfigurable(final @NotNull FrameworkSupportModel model) {
    return new FrameworkSupportConfigurableBase(this, model, getVersions(), getVersionLabelText());
  }
}
