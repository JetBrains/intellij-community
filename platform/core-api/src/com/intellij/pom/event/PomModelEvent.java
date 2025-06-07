// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.event;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.EventObject;
import java.util.Set;

public class PomModelEvent extends EventObject {
  private PomChangeSet myChangeSet;

  @ApiStatus.Internal
  public PomModelEvent(@NotNull PomModel source, @Nullable PomChangeSet changeSet) {
    super(source);
    myChangeSet = changeSet;
  }

  public @NotNull @Unmodifiable Set<PomModelAspect> getChangedAspects() {
    if (myChangeSet != null) {
      return Collections.singleton(myChangeSet.getAspect());
    }
    else {
      return Collections.emptySet();
    }
  }

  public PomChangeSet getChangeSet(@NotNull PomModelAspect aspect) {
    return myChangeSet == null || !aspect.equals(myChangeSet.getAspect()) ? null : myChangeSet;
  }

  public void merge(@NotNull PomModelEvent event) {
    if (event.myChangeSet != null && myChangeSet != null) {
      myChangeSet.merge(event.myChangeSet);
    } else if (myChangeSet == null) {
      myChangeSet = event.myChangeSet;
    }
  }

  @Override
  public PomModel getSource() {
    return (PomModel)super.getSource();
  }

  public void beforeNestedTransaction() {
    if (myChangeSet != null) {
      myChangeSet.beforeNestedTransaction();
    }
  }
}
