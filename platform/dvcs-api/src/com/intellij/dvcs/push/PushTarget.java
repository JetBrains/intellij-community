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
package com.intellij.dvcs.push;

import org.jetbrains.annotations.NotNull;

/**
 * Destination for push action. (Remote  for git or push-path for mercurial).
 */
public interface PushTarget {

  /**
   * Returns true if pushing to this target is guaranteed to introduce something new: e.g. new branch or tag.
   * <p/>
   * Returning false doesn't mean that this target has nothing to push (e.g. commits to push are calculated separately),
   * it means rather that "we don't know".
   */
  boolean hasSomethingToPush();

  @NotNull
  String getPresentation();
}
