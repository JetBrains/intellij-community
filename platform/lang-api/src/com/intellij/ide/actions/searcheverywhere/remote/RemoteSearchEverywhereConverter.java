// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.remote;

public interface RemoteSearchEverywhereConverter<I, P extends RemoteSearchEverywherePresentation> {

  P convertToPresentation(I item);

  I convertToItem(P presentation);

}
