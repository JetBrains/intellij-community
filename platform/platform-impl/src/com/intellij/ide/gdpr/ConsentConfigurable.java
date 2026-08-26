// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ide.gdpr.ConsentsUiLoadingKt.applyConsentsFromConfigurable;
import static com.intellij.ide.gdpr.ConsentsUiLoadingKt.loadConsentsForConfigurable;

@ApiStatus.Internal
public final class ConsentConfigurable extends ConfigurableBase<ConsentSettingsUi, ConsentsState> {
  private ConsentsState myConsents = null;

  public ConsentConfigurable() {
    super("consents", IdeBundle.message("consent.configurable"), "preferences.usage.statistics");
  }

  @Override
  public void reset() {
    if (myConsents == null) {
      myConsents = loadConsentsForConfigurable();
    }

    super.reset();
  }

  @Override
  protected @NotNull ConsentsState getSettings() {
    return myConsents;
  }

  @Override
  protected ConsentSettingsUi createUi() {
    ConsentSettingsUi ui = new ConsentSettingsUi(true) {
      @Override
      public void apply(@NotNull ConsentsState consents) {
        super.apply(consents);

        applyConsentsFromConfigurable(consents);
      }
    };

    // When building searchable options, ensure we return a non-empty UI
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myConsents = loadConsentsForConfigurable();
      ui.reset(myConsents);
    }
    return ui;
  }
}
