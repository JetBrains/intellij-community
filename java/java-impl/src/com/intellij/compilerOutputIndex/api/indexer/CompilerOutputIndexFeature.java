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
package com.intellij.compilerOutputIndex.api.indexer;

import com.intellij.compilerOutputIndex.impl.MethodsUsageIndex;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("unchecked")
public enum CompilerOutputIndexFeature {
  METHOD_CHAINS_COMPLETION("completion.enable.relevant.method.chain.suggestions", ContainerUtil
    .<Class<? extends CompilerOutputBaseIndex>>newArrayList(MethodsUsageIndex.class));

  @NotNull
  private final String myKey;
  @NotNull
  private final Collection<Class<? extends CompilerOutputBaseIndex>> myRequiredIndexes;

  CompilerOutputIndexFeature(@NotNull final String key,
                             @NotNull final Collection<Class<? extends CompilerOutputBaseIndex>> requiredIndexes) {
    myKey = key;
    myRequiredIndexes = requiredIndexes;
  }

  CompilerOutputIndexFeature(@NotNull final String key, @NotNull final Class<? extends CompilerOutputBaseIndex> requiredIndex) {
    this(key, Collections.<Class<? extends CompilerOutputBaseIndex>>singleton(requiredIndex));
  }

  public RegistryValue getRegistryValue() {
    return Registry.get(myKey);
  }

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
  public Collection<Class<? extends CompilerOutputBaseIndex>> getRequiredIndexes() {
    return myRequiredIndexes;
  }
}
