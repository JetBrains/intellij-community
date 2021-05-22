// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.GeneralSettingsConfigurableKt;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

final class SystemOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  @Override
  public @NotNull Collection<OptionDescription> getOptions() {
    return ContainerUtil.concat(
      GeneralSettingsConfigurableKt.getAllOptionDescriptors(),
      List.of(
        option(OptionsTopHitProvider.messageIde("option.show.tips.on.startup"), "isShowTipsOnStartup", "setShowTipsOnStartup"),
        option(OptionsTopHitProvider.messageIde("checkbox.support.screen.readers"), "isSupportScreenReaders", "setSupportScreenReaders"),
        option(OptionsTopHitProvider.messageIde("label.start.search.in.background"), "isSearchInBackground", "setSearchInBackground")
      )
    );
  }

  @Override
  public @NotNull String getId() {
    return "system";
  }

  static BooleanOptionDescription option(@Nls String option, @NonNls String getter, @NonNls String setter) {
    return new PublicMethodBasedOptionDescription(option, "preferences.general", getter, setter, () -> GeneralSettings.getInstance());
  }
}
