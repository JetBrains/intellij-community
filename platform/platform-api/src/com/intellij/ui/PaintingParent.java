/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Used for deferred re-painting (i.e. for deferred icons). As a paint() method
 * is invoked through the normal AWT painting cycle, deferred painting of
 * children may be queued. It means that actual data used for painting is pushed
 * for calculation and, as it gets ready, there is need for repaint.
 *
 * The target component for further repaint() is either the component that was
 * originally exposed to painting or, if it's no longer showing (in case of a flyweight
 * renderer) -- the first component up in the hierarchy that implements PaintingParent.
 *
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public interface PaintingParent {

  /**
   * Returns rectangle of a child component for further repainting
   * @param c a component
   * @return a rectangle, if null -- the whole component will be repainted
   */
  @Nullable
  Rectangle getChildRec(@NotNull Component c);

  class Wrapper extends JPanel implements PaintingParent {
    public Wrapper(@NotNull Component component) {
      super(new BorderLayout(0,0));
      add(component);
    }

    @Override
    public Rectangle getChildRec(@NotNull Component c) {
      return null;
    }
  }

}
