// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;

import java.util.Collection;

public final class BuildingTargetProgressMessage extends BuildMessage {
  private final Collection<? extends BuildTarget<?>> myTargets;
  private final @NotNull Event myEventType;

  public enum Event {
    STARTED, FINISHED
  }

  public BuildingTargetProgressMessage(@NotNull Collection<? extends BuildTarget<?>> targets, @NotNull Event event) {
    super(composeMessageText(targets, event), Kind.PROGRESS);
    myTargets = targets;
    myEventType = event;
  }

  private static String composeMessageText(Collection<? extends BuildTarget<?>> targets, Event event) {
    String targetsString = StringUtil.join(targets, dom -> dom.getPresentableName(), ", ");
    return (event == Event.STARTED ? "Started" : "Finished") + " building " + targetsString;
  }

  public @NotNull Collection<? extends BuildTarget<?>> getTargets() {
    return myTargets;
  }

  public @NotNull Event getEventType() {
    return myEventType;
  }
}
