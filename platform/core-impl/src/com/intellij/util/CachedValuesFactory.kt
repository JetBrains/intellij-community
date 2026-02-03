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
package com.intellij.util

import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CachedValuesFactory {
  fun <T> createCachedValue(provider: CachedValueProvider<T>, trackValue: Boolean): CachedValue<T>

  fun <T> createCachedValue(userDataHolder: UserDataHolder, provider: CachedValueProvider<T>, trackValue: Boolean): CachedValue<T>

  fun <T, P> createParameterizedCachedValue(provider: ParameterizedCachedValueProvider<T, P>,
                                            trackValue: Boolean): ParameterizedCachedValue<T, P>

  fun <T, P> createParameterizedCachedValue(userDataHolder: UserDataHolder,
                                            provider: ParameterizedCachedValueProvider<T, P>,
                                            trackValue: Boolean): ParameterizedCachedValue<T, P>
}
