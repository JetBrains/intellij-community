/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.openapi.ide.dnd;

import jetbrains.fabrique.util.ComparableObject;
import jetbrains.fabrique.openapi.ide.dnd.DnDEvent;

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
