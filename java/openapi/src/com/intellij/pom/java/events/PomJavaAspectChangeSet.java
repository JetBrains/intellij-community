/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.pom.java.events;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.java.PomJavaAspect;
import org.jetbrains.annotations.NotNull;

public class PomJavaAspectChangeSet implements PomChangeSet{
  private final PomModel myModel;

  public PomJavaAspectChangeSet(PomModel model) {
    myModel = model;
  }

  @NotNull
  public PomModelAspect getAspect() {
    return myModel.getModelAspect(PomJavaAspect.class);
  }

  public void merge(@NotNull PomChangeSet blocked) {
  }
}
