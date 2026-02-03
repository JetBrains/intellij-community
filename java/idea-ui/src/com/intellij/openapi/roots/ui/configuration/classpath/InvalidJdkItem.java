// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.roots.OrderEntry;

final class InvalidJdkItem extends ClasspathTableItem<OrderEntry> {
  InvalidJdkItem() {
    super(null, false);
  }
}
