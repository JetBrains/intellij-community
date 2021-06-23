// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface LogEventDetector {
  @NotNull String featureId();

  @NotNull String groupId();

  @NotNull String eventId();

  boolean succeed(@NotNull Map<String, Object> eventData);

  static LogEventDetector create(Type type, @NotNull String featureId, @NotNull String eventDataId) {
    switch (type) {
      case ACTION: return new Actions(featureId, eventDataId);
      case INTENTION: return new Intentions(featureId, eventDataId);
    }
    throw new IllegalArgumentException("Unknown Log event type: " + type.name());
  }


  class Actions implements LogEventDetector {
    private static final String ID_KEY = "action_id";
    private final String myFeatureId;
    private final String myActionId;

    public Actions(@NotNull String featureId, @NotNull String actionId) {
      myFeatureId = featureId;
      myActionId = actionId;
    }

    @Override
    final public @NotNull String featureId() {
      return myFeatureId;
    }

    @Override
    final public @NotNull String groupId() {
      return "actions";
    }

    @Override
    final public @NotNull String eventId() {
      return "action.finished";
    }

    @Override
    public boolean succeed(@NotNull Map<String, Object> eventData) {
      return eventData.get(ID_KEY).equals(myActionId);
    }
  }


  class Intentions implements LogEventDetector {
    private static final String ID_KEY = "id";
    private final String myFeatureId;
    private final String myIntentionId;

    public Intentions(@NotNull String featureId, @NotNull String intentionId) {
      myFeatureId = featureId;
      myIntentionId = intentionId;
    }

    @Override
    final public @NotNull String featureId() {
      return myFeatureId;
    }

    @Override
    final public @NotNull String groupId() {
      return "intentions";
    }

    @Override
    final public @NotNull String eventId() {
      return "called";
    }

    @Override
    public boolean succeed(@NotNull Map<String, Object> eventData) {
      return eventData.get(ID_KEY).equals(myIntentionId);
    }
  }

  enum Type {
    ACTION,
    INTENTION
  }
}
