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
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;

/**
 * This component provides the notion of last editor action.
 * Its purpose is to be able to determine whether some action was performed right after another specific action.
 * <p>
 * It's supposed to be used from EDT only.
 */
public abstract class EditorLastActionTracker {
  public static EditorLastActionTracker getInstance() {
    return ApplicationManager.getApplication().getComponent(EditorLastActionTracker.class);
  }

  /**
   * Returns the id of the previously invoked action or <code>null</code>, if no history exists yet, or last user activity was of
   * non-action type, like mouse clicking in editor or text typing, or previous action was invoked for a different editor.
   */
  @Nullable
  public abstract String getLastActionId();
}
