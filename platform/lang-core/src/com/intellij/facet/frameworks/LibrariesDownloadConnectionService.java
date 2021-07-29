// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.frameworks;

public final class LibrariesDownloadConnectionService extends SettingsConnectionService {

  private static final String SETTINGS_URL = "https://www.jetbrains.com/idea/download-assistant.xml";
  private static final String SERVICE_URL = "https://frameworks.jetbrains.com";

  private static final LibrariesDownloadConnectionService ourInstance = new LibrariesDownloadConnectionService();

  public static LibrariesDownloadConnectionService getInstance() {
    return ourInstance;
  }

  private LibrariesDownloadConnectionService() {
    super(SETTINGS_URL, SERVICE_URL);
  }

}
