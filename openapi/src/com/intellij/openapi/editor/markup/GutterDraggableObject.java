/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import java.awt.*;

/**
 * @author ven
 */
public interface GutterDraggableObject {

  public void removeSelf();

  public boolean copy (int line);

  Cursor getCursor();
}
