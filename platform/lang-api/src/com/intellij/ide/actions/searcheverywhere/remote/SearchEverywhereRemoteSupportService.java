// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.remote;

import com.intellij.openapi.application.ApplicationManager;

import java.util.List;

public interface SearchEverywhereRemoteSupportService {

  static SearchEverywhereRemoteSupportService getInstance() {
    return ApplicationManager.getApplication().getService(SearchEverywhereRemoteSupportService.class);
  }

  List<RemoteSearchEverywhereConverter<?, ?>> getConverters(String contributorID);
}
