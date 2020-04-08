// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface BooleanBiFunction<Param1, Param2> extends BiFunction<Boolean, Param1, Param2> {
}
