// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.meta;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface MetaDataContributor {
  ExtensionPointName<MetaDataContributor> EP_NAME = ExtensionPointName.create("com.intellij.metaDataContributor");

  void contributeMetaData(@NotNull MetaDataRegistrar registrar);
}
