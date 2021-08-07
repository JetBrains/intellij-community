// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ThreeState;


@State(
  name = "libraryTable",
  storages = @Storage(value = "applicationLibraries.xml", roamingType = RoamingType.DISABLED, useSaveThreshold = ThreeState.NO)
)
public class ExportableApplicationLibraryTable extends ApplicationLibraryTable {
}
