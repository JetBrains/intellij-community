/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.pom.event;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NotNull
  public Set<PomModelAspect> getChangedAspects() {
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
