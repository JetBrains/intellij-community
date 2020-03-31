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
package com.intellij.openapi.util;

import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.NotNull;

public abstract class ActiveRunnable extends ComparableObject.Impl {

  protected ActiveRunnable() {
  }

  protected ActiveRunnable(@NotNull Object object) {
    super(object);
  }

  protected ActiveRunnable(Object @NotNull [] objects) {
    super(objects);
  }

  @NotNull
  public abstract ActionCallback run();
}