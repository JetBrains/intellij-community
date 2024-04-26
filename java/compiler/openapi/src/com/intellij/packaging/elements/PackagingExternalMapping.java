// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.elements;

import com.intellij.platform.workspace.storage.ExternalMappingKey;

public final class PackagingExternalMapping {
  public final static ExternalMappingKey<PackagingElement<?>> key =
    ExternalMappingKey.Companion.create("intellij.artifacts.packaging.elements");
}
