// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @see Region
 */
@ApiStatus.Experimental
public final class RegionSettings {
  /**
   * A Preferences key to access region code
   */
  private static final String REGION_CODE_KEY = "JetBrains.region.code";

  private RegionSettings() {
  }

  @ApiStatus.Internal
  public static void setRegion(@NotNull Region region) {
    if (region == Region.NOT_SET) {
      resetCode();
    }
    else {
      Prefs.put(REGION_CODE_KEY, region.externalName());
      fireEvent();
    }
  }

  public static @NotNull Region getRegion() {
    String name = Prefs.get(REGION_CODE_KEY, Region.NOT_SET.externalName());
    return Region.fromExternalName(name);
  }

  /**
   * Clear region setting
   */
  @ApiStatus.Internal
  public static void resetCode() {
    Prefs.remove(REGION_CODE_KEY);
    fireEvent();
  }

  private static void fireEvent() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(RegionSettingsListener.UPDATE_TOPIC).regionChanged();
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public interface RegionSettingsListener {
    @Topic.AppLevel
    Topic<RegionSettingsListener> UPDATE_TOPIC = new Topic<>(RegionSettingsListener.class, Topic.BroadcastDirection.NONE, true);

    void regionChanged();
  }
}
