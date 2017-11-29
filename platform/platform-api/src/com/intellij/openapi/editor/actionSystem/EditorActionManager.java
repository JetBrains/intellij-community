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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to register actions which are activated by typing and navigation keystrokes
 * in the editor.
 */
public abstract class EditorActionManager {
  /**
   * Returns the instance of the editor action manager.
   *
   * @return the editor action manger instance.
   */
  public static EditorActionManager getInstance() {
    return ServiceManager.getService(EditorActionManager.class);
  }

  /**
   * Returns the handler currently defined for the specified editor actions.
   *
   * @param actionId the ID of the action for which the handler is requested. Possible
   *                 IDs are defined in the {@link com.intellij.openapi.actionSystem.IdeActions} class
   *                 by constants starting with {@code ACTION_EDITOR_}.
   * @return the handler currently defined for the action.
   */
  public abstract EditorActionHandler getActionHandler(@NonNls @NotNull String actionId);

  /**
   * Sets the handler for the specified editor actions.
   *
   * @param actionId the ID of the action for which the handler is set. Possible
   *                 IDs are defined in the {@link com.intellij.openapi.actionSystem.IdeActions} class
   *                 by constants starting with {@code ACTION_EDITOR_}.
   * @return the handler previously defined for the action.
   */
  public abstract EditorActionHandler setActionHandler(@NonNls @NotNull String actionId, @NotNull EditorActionHandler handler);

  /**
   * Returns the service for registering actions activated by typing visible characters
   * in the editor.
   *
   * @return the typed action service instance.
   */
  @NotNull
  public abstract TypedAction getTypedAction();

  /**
   * Gets the default handler which is invoked on attempt to modify a read-only fragment in the editor.
   *
   * @return the handler instance.
   * @see com.intellij.openapi.editor.Document#createGuardedBlock(int, int)
   */
  public abstract ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler();

  /**
   * Sets the handler which is invoked on attempt to modify a read-only fragment in the editor.
   *
   * @param handler the handler instance.
   * @return the old instance of the handler.
   * @see com.intellij.openapi.editor.Document#createGuardedBlock(int, int)
   */
  public abstract ReadonlyFragmentModificationHandler setReadonlyFragmentModificationHandler(@NotNull ReadonlyFragmentModificationHandler handler);

  /**
   * Gets the handler which is invoked on attempt to modify a read-only fragment for the document.
   *
   * @param document target document
   * @return the handler instance.
   * @see com.intellij.openapi.editor.Document#createGuardedBlock(int, int)
   */
  public abstract ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler(@NotNull Document document);

  /**
   * Sets the handler which is invoked on attempt to modify a read-only fragment for the document.
   *
   * @param document target document
   * @param handler  new handler
   * @see com.intellij.openapi.editor.Document#createGuardedBlock(int, int)
   */
  public abstract void setReadonlyFragmentModificationHandler(@NotNull Document document, ReadonlyFragmentModificationHandler handler);

}

