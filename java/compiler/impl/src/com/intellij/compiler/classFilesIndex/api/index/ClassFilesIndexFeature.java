/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.api.index;

import com.intellij.compiler.classFilesIndex.impl.MethodsUsageIndexConfigure;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("unchecked")
public enum ClassFilesIndexFeature {
  METHOD_CHAINS_COMPLETION("completion.enable.relevant.method.chain.suggestions", MethodsUsageIndexConfigure.INSTANCE);

  @NotNull
  private final String myKey;
  @NotNull
  private final Collection<? extends ClassFilesIndexConfigure> myRequiredIndicesConfigures;

  ClassFilesIndexFeature(@NotNull final String key,
                         @NotNull final Collection<? extends ClassFilesIndexConfigure> requiredIndicesConfigures) {
    myKey = key;
    myRequiredIndicesConfigures = requiredIndicesConfigures;
  }

  ClassFilesIndexFeature(@NotNull final String key, @NotNull final ClassFilesIndexConfigure requiredConfigure) {
    this(key, Collections.<ClassFilesIndexConfigure>singleton(requiredConfigure));
  }

  public RegistryValue getRegistryValue() {
    return Registry.get(myKey);
  }

  @NotNull
  public String getKey() {
    return myKey;
  }

  /**
   * is feature enabled by registry key
   */
  public boolean isEnabled() {
    return Registry.is(myKey);
  }

  public void enable() {
    getRegistryValue().setValue(true);
  }

  public void disable() {
    getRegistryValue().setValue(false);
  }

  @NotNull
  public Collection<? extends ClassFilesIndexConfigure> getRequiredIndicesConfigures() {
    return myRequiredIndicesConfigures;
  }
}
