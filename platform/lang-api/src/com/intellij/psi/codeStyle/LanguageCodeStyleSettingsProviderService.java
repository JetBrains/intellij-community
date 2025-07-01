// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;

@Service(Service.Level.APP)
final class LanguageCodeStyleSettingsProviderService implements Disposable {
  static LanguageCodeStyleSettingsProviderService getInstance() {
    return ApplicationManager.getApplication().getService(LanguageCodeStyleSettingsProviderService.class);
  }

  private volatile List<LanguageCodeStyleSettingsProvider> allProviders;
  private final Object lock = new Object();

  @SuppressWarnings("unused")
  LanguageCodeStyleSettingsProviderService(@NotNull CoroutineScope coroutineScope) {
    LanguageCodeStyleSettingsProvider.EP_NAME.addChangeListener(coroutineScope, () -> {
      allProviders = null;
      LanguageCodeStyleSettingsProvider.cleanSettingsPagesProvidersCache();
    });

    LanguageCodeStyleSettingsContributor.EP_NAME.addChangeListener(coroutineScope, () -> {
      allProviders = null;
    });
  }

  @TestOnly
  LanguageCodeStyleSettingsProviderService(@SuppressWarnings("unused") boolean testOnly) {
  }

  public @NotNull List<LanguageCodeStyleSettingsProvider> getAllProviders() {
    List<LanguageCodeStyleSettingsProvider> providers = allProviders;
    if (providers != null) return providers;

    synchronized (lock) {
      providers = allProviders;
      if (providers != null) return providers;

      allProviders = providers = computeAllProviders();
    }

    return providers;
  }

  private static @NotNull List<LanguageCodeStyleSettingsProvider> computeAllProviders() {
    List<LanguageCodeStyleSettingsProvider> extensions = LanguageCodeStyleSettingsProvider.EP_NAME.getExtensionList();

    List<LanguageCodeStyleSettingsContributor> contributors = LanguageCodeStyleSettingsContributor.EP_NAME.getExtensionList();
    if (contributors.isEmpty()) {
      return extensions;
    }

    List<LanguageCodeStyleSettingsProvider> allExtensions = new ArrayList<>(extensions);
    for (LanguageCodeStyleSettingsContributor contributor : contributors) {
      allExtensions.addAll(contributor.getProviders());
    }

    return allExtensions;
  }

  @Override
  public void dispose() {
  }
}
