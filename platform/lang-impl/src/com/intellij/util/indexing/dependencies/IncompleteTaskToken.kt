// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * This class is to solve a technical problem: you cannot obtain ScanningRequestToken from EDT, because
 * obtaining a token may trigger fingerprint calculation. IncompleteTaskToken is EDT-safe.
 */
@Internal
class IncompleteTaskToken