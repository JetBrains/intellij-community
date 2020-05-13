// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public interface DnDTarget extends DnDDropHandler, DnDTargetChecker {
  default void cleanUpOnLeave() {
  }

  default void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
  }
}
