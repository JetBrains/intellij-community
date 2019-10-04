// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.GeneralSettingsConfigurableKt;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.ide.ui.OptionsTopHitProvider.messageIde;

/**
 * @author Sergey.Malenkov
 */
final class SystemOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  private static final Collection<OptionDescription> ourOptions = Collections.unmodifiableCollection(
    ContainerUtil.concat(
      GeneralSettingsConfigurableKt.getAllOptionDescriptors(),
      Arrays.asList(
        option(messageIde("checkbox.show.tips.on.startup"), "isShowTipsOnStartup", "setShowTipsOnStartup"),
        option(messageIde("checkbox.support.screen.readers"), "isSupportScreenReaders", "setSupportScreenReaders"),
        option("Start search in background", "isSearchInBackground", "setSearchInBackground")
      )
    ));

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions() {
    return ourOptions;
  }

  @NotNull
  @Override
  public String getId() {
    return "system";
  }

  static BooleanOptionDescription option(String option, String getter, String setter) {
    return new PublicMethodBasedOptionDescription(option, "preferences.general", getter, setter) {
      @NotNull
      @Override
      public Object getInstance() {
        return GeneralSettings.getInstance();
      }
    };
  }
}
