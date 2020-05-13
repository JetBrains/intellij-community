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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.psi.PsiElement;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StubIndexKey<K, Psi extends PsiElement> extends ID<K, Psi> {
  private StubIndexKey(@NonNls String name, @Nullable PluginId pluginId) {
    super(name, pluginId);
  }

  @NotNull
  public static synchronized <K, Psi extends PsiElement> StubIndexKey<K, Psi> createIndexKey(@NonNls @NotNull String name) {
    PluginId pluginId = getCallerPluginId();
    ID<?, ?> existing = findByName(name, true, pluginId);
    if (existing != null) {
      if (existing instanceof StubIndexKey) {
        return (StubIndexKey<K, Psi>) existing;
      }
      throw new IllegalStateException("key with id " + name + " is already registered", existing.getRegistrationTrace());
    }
    return new StubIndexKey<>(name, pluginId);
  }

}