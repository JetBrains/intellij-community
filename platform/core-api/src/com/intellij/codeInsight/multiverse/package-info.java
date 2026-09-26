// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Modern build tools like Bazel or Gradle allow compiling a source file within several contexts (in other words, to include a file
 * into several modules at once). Thus, we need a way to represent this fact in IntelliJ model.
 * <p/>
 * This API is highly experimental and thus mark as internal for a while.
 */
@ApiStatus.Experimental
package com.intellij.codeInsight.multiverse;

import org.jetbrains.annotations.ApiStatus;

