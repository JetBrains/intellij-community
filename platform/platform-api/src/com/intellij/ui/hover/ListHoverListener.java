// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.hover;

import com.intellij.openapi.util.Key;
import com.intellij.ui.render.RenderingUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JList;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

@ApiStatus.Experimental
public abstract class ListHoverListener extends HoverListener {
  public abstract void onHover(@NotNull JList<?> list, int index);

  @Override
  public final void mouseEntered(@NotNull Component component, int x, int y) {
    mouseMoved(component, x, y);
  }

  @Override
  public final void mouseMoved(@NotNull Component component, int x, int y) {
    update(component, list -> list.locationToIndex(new Point(x, y)));
  }

  @Override
  public final void mouseExited(@NotNull Component component) {
    update(component, list -> -1);
  }


  private final AtomicInteger indexHolder = new AtomicInteger(-1);

  private void update(@NotNull Component component, @NotNull ToIntFunction<? super JList<?>> indexFunc) {
    if (component instanceof JList) {
      JList<?> list = (JList<?>)component;
      int indexNew = indexFunc.applyAsInt(list);
      int indexOld = indexHolder.getAndSet(indexNew);
      if (indexNew != indexOld) onHover(list, indexNew);
    }
  }


  private static final Key<Integer> HOVERED_INDEX_KEY = Key.create("ListHoveredIndex");
  public static final HoverListener DEFAULT = new ListHoverListener() {
    @Override
    public void onHover(@NotNull JList<?> list, int index) {
      setHoveredIndex(list, index);
    }
  };

  @ApiStatus.Internal
  static void setHoveredIndex(@NotNull JList<?> list, int indexNew) {
    int indexOld = getHoveredIndex(list);
    if (indexNew == indexOld) return;
    list.putClientProperty(HOVERED_INDEX_KEY, indexNew < 0 ? null : indexNew);
    if (RenderingUtil.isHoverPaintingDisabled(list)) return;
    repaintIndex(list, indexOld);
    repaintIndex(list, indexNew);
  }

  /**
   * @param list a list, which hover state is interesting
   * @return an index of a hovered item of the specified list
   * @see #DEFAULT
   */
  public static int getHoveredIndex(@NotNull JList<?> list) {
    Object property = list.getClientProperty(HOVERED_INDEX_KEY);
    return property instanceof Integer ? (Integer)property : -1;
  }

  private static void repaintIndex(@NotNull JList<?> list, int index) {
    Rectangle bounds = index < 0 ? null : list.getCellBounds(index, index);
    if (bounds != null) list.repaint(0, bounds.y, list.getWidth(), bounds.height);
  }
}
