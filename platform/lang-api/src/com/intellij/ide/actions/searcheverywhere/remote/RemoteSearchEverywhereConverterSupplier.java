// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.remote;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

/**
 * @deprecated The old Search Everywhere API is being sunset. This functionality is obsolete.
 */
@Deprecated
public interface RemoteSearchEverywhereConverterSupplier<I, P extends RemoteSearchEverywherePresentation> {

  ExtensionPointName<RemoteSearchEverywhereConverterSupplier<?, ?>> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereRemoteConverter");

  List<String> contributorsList();

  RemoteSearchEverywhereConverter<I, P> createConverter();
}
