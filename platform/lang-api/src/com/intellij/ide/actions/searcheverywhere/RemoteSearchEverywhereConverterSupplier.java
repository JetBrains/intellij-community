// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

public interface RemoteSearchEverywhereConverterSupplier<Item, Presentation> {

  ExtensionPointName<RemoteSearchEverywhereConverterSupplier<?, ?>> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereRemoteConverter");

  List<String> contributorsList();

  RemoteSearchEverywhereConverter<Item, Presentation> createConverter();
}
