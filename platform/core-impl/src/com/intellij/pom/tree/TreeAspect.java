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
package com.intellij.pom.tree;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class TreeAspect implements PomModelAspect {
  public TreeAspect(@NotNull PomModel model) {
    model.registerAspect(TreeAspect.class, this, Collections.<PomModelAspect>emptySet());
  }

  @Override
  public void update(PomModelEvent event) {}
}
