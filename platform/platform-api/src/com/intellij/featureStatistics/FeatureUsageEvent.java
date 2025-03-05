// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class FeatureUsageEvent {
  protected final String myFeatureId;

  protected FeatureUsageEvent(@NonNls @NotNull String featureId) {
    myFeatureId = featureId;
  }

  public @NotNull String featureId() {
    return myFeatureId;
  }

  static @NotNull Action createActionEvent(@NonNls @NotNull String featureId, @NonNls @NotNull String actionId) {
    return new Action(featureId, actionId);
  }

  static @NotNull Intention createIntentionEvent(@NonNls @NotNull String featureId, @NonNls @NotNull String intentionClassName) {
    return new Intention(featureId, intentionClassName);
  }

  @ApiStatus.Internal
  public static final class Action extends FeatureUsageEvent {
    private final String myActionId;

    private Action(@NotNull String featureId, @NotNull String actionId) {
      super(featureId);
      myActionId = actionId;
    }

    @ApiStatus.Internal
    public @NotNull String getActionId() {
      return myActionId;
    }
  }

  @ApiStatus.Internal
  public static final class Intention extends FeatureUsageEvent {
    private final String myIntentionClassName;

    private Intention(@NotNull String featureId, @NotNull String intentionClassName) {
      super(featureId);
      myIntentionClassName = intentionClassName;
    }

    public @NotNull String getIntentionClassName() {
      return myIntentionClassName;
    }
  }
}
