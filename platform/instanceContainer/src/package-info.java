// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Instance container is essentially a map with {@link java.lang.Class} as a key, and instance as a value.
 * The value is usually computed lazily unless an eager instance was registered instead of an initializer for a given key.
 * Lazy initialization can track {@linkplain com.intellij.platform.instanceContainer.CycleInitializationException cycles}:
 * situations when initialization of an instance depends on itself possibly via a chain of other initializers.
 * <p/>
 * This module defines {@linkplain com.intellij.platform.instanceContainer.InstanceContainer the public interface of instance container},
 * {@linkplain com.intellij.platform.instanceContainer.internal.InstanceContainerInternal the internal interface},
 * {@linkplain com.intellij.platform.instanceContainer.internal.MutableInstanceContainer the internal interface for mutation},
 * and {@linkplain com.intellij.platform.instanceContainer.internal.InstanceContainerImpl implementation} of mentioned interfaces.
 */
@Experimental
package com.intellij.platform.instanceContainer;

import org.jetbrains.annotations.ApiStatus.Experimental;
