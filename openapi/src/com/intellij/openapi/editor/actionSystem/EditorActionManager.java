/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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

