/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.openapi.ide.dnd;

import com.intellij.util.ui.update.ComparableObject;

public abstract class DropActionHandler extends ComparableObject.Impl {

  protected DropActionHandler() {
  }

  protected DropActionHandler(Object handlerEqualityObject) {
    super(handlerEqualityObject);
  }

  protected DropActionHandler(Object[] handlerEqualityObjects) {
    super(handlerEqualityObjects);
  }

  public abstract void performDrop(DnDEvent aEvent);

}
