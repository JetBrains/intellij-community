// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

abstract class FeatureUsageEvent {
  protected final String myFeatureId;

  protected FeatureUsageEvent(@NonNls @NotNull String featureId) {
    myFeatureId = featureId;
  }

  @NotNull String featureId() {
    return myFeatureId;
  }

  static @NotNull Action createActionEvent(@NonNls @NotNull String featureId, @NonNls @NotNull String actionId) {
    return new Action(featureId, actionId);
  }

  static @NotNull Intention createIntentionEvent(@NonNls @NotNull String featureId, @NonNls @NotNull String intentionClassName) {
    return new Intention(featureId, intentionClassName);
  }

  static class Action extends FeatureUsageEvent {
    private final String myActionId;

    private Action(@NotNull String featureId, @NotNull String actionId) {
      super(featureId);
      myActionId = actionId;
    }

    @NotNull String getActionId() {
      return myActionId;
    }
  }

  static class Intention extends FeatureUsageEvent {
    private final String myIntentionClassName;

    private Intention(@NotNull String featureId, @NotNull String intentionClassName) {
      super(featureId);
      myIntentionClassName = intentionClassName;
    }

    @NotNull String getIntentionClassName() {
      return myIntentionClassName;
    }
  }
}
