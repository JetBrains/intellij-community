// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
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
    return ApplicationManager.getApplication().getService(EditorActionManager.class);
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
   * @deprecated Use {@link TypedAction#getInstance()} instead
   */
  @Deprecated
  @NotNull
  public abstract TypedAction getTypedAction();

  /**
   * Gets the default handler which is invoked on attempt to modify a read-only fragment in the editor.
   *
   * @return the handler instance.
   * @see Document#createGuardedBlock(int, int)
   */
  public abstract ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler();

  /**
   * Sets the handler which is invoked on attempt to modify a read-only fragment in the editor.
   *
   * @param handler the handler instance.
   * @return the old instance of the handler.
   * @see Document#createGuardedBlock(int, int)
   */
  public abstract ReadonlyFragmentModificationHandler setReadonlyFragmentModificationHandler(@NotNull ReadonlyFragmentModificationHandler handler);

  /**
   * Gets the handler which is invoked on attempt to modify a read-only fragment for the document.
   *
   * @param document target document
   * @return the handler instance.
   * @see Document#createGuardedBlock(int, int)
   */
  public abstract ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler(@NotNull Document document);

  /**
   * Sets the handler which is invoked on attempt to modify a read-only fragment for the document.
   *
   * @param document target document
   * @param handler  new handler
   * @see Document#createGuardedBlock(int, int)
   */
  public abstract void setReadonlyFragmentModificationHandler(@NotNull Document document, ReadonlyFragmentModificationHandler handler);

}

