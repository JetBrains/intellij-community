// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.util

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink

internal fun Location.toLocationLink(): LocationLink = LocationLink(uri, range, range)
