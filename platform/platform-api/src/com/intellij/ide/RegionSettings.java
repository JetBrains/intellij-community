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

  @ApiStatus.Internal
  @Topic.AppLevel
  public static final Topic<Runnable> UPDATE_TOPIC = new Topic<>(Runnable.class, Topic.BroadcastDirection.NONE, true);

  private RegionSettings() {
  }

  @ApiStatus.Internal
  public static void setRegion(@NotNull Region region) {
    if (region == Region.NOT_SET) {
      resetCode();
    }
    else {
      Prefs.put(REGION_CODE_KEY, region.name());
      fireEvent();
    }
  }

  public static @NotNull Region getRegion() {
    try {
      String name = Prefs.get(REGION_CODE_KEY, Region.NOT_SET.name());
      return Region.valueOf(name);
    }
    catch (IllegalArgumentException e) {
      return Region.NOT_SET;
    }
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
    ApplicationManager.getApplication().getMessageBus().syncPublisher(UPDATE_TOPIC).run();
  }
}
