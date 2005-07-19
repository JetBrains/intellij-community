/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.application.ApplicationManager;

public abstract class EditorActionManager {
  public static EditorActionManager getInstance() {
    return ApplicationManager.getApplication().getComponent(EditorActionManager.class);
  }

  public abstract EditorActionHandler getActionHandler(String actionId);
  public abstract EditorActionHandler setActionHandler(String actionId, EditorActionHandler handler);

  public abstract TypedAction getTypedAction();

  public abstract ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler();
  public abstract ReadonlyFragmentModificationHandler setReadonlyFragmentModificationHandler(ReadonlyFragmentModificationHandler handler);
}

