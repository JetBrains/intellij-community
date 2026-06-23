// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.accessibility.LinuxAccessibilitySupport;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.GeneralSettingsConfigurableKt;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

final class SystemOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  @Override
  public @Unmodifiable @NotNull Collection<OptionDescription> getOptions() {
    return ContainerUtil.concat(
      GeneralSettingsConfigurableKt.getAllOptionDescriptors(),
      List.of(
        option(OptionsTopHitProvider.messageIde("option.show.tips.on.startup"), "isShowTipsOnStartup", "setShowTipsOnStartup"),
        screenReaderOption(OptionsTopHitProvider.messageIde("checkbox.support.screen.readers")),
        option(OptionsTopHitProvider.messageIde("label.start.search.in.background"), "isSearchInBackground", "setSearchInBackground")
      )
    );
  }

  @Override
  public @NotNull String getId() {
    return "system";
  }

  static BooleanOptionDescription option(@Nls String option, @NonNls String getter, @NonNls String setter) {
    return new PublicMethodBasedOptionDescription(option, "preferences.general", getter, setter, GeneralSettings::getInstance);
  }

  private static BooleanOptionDescription screenReaderOption(@Nls String option) {
    return new PublicMethodBasedOptionDescription(option, "preferences.general", "isSupportScreenReaders", "setSupportScreenReaders",
                                                  GeneralSettings::getInstance) {
      @Override
      public void setOptionState(boolean enabled) {
        boolean oldValue = isOptionEnabled();
        super.setOptionState(enabled);

        boolean newValue = isOptionEnabled();
        if (oldValue != newValue) {
          LinuxAccessibilitySupport.syncAtkWrapperVmOption(newValue);
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            RegistryBooleanOptionDescriptor.suggestRestart(null);
          }
        }
      }
    };
  }
}
