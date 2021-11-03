// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;

/**
 * @deprecated Use {@link HashMap}
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@Deprecated
public class SmartHashMap<K, V> extends HashMap<K, V> {
}
