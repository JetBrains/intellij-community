// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.AppMode;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class WhatsNewUtil {
  @ApiStatus.Internal
  public static boolean isWhatsNewAvailable() {
    return (UpdateStrategyCustomization.getInstance().getShowWhatIsNewPageAfterUpdate() || ApplicationInfoEx.getInstanceEx().isShowWhatsNewOnUpdate())
           && ExternalProductResourceUrls.getInstance().getWhatIsNewPageUrl() != null
           && !AppMode.isRemoteDevHost();
  }
}