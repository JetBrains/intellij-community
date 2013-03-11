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

package com.intellij.openapi.ui;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class AbstractPainter implements Painter {

  private boolean myNeedsRepaint;

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();


  public boolean needsRepaint() {
    return myNeedsRepaint;
  }

  public void setNeedsRepaint(final boolean needsRepaint) {
    setNeedsRepaint(needsRepaint, null);
  }

  public void setNeedsRepaint(final boolean needsRepaint, @Nullable JComponent dirtyComponent) {
    myNeedsRepaint = needsRepaint;
    if (myNeedsRepaint) {
      fireNeedsRepaint(dirtyComponent);
    }
  }

  public void addListener(final Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(final Listener listener) {
    myListeners.remove(listener);
  }

  @Nullable
  public <T> T setNeedsRepaint(T oldValue, T newValue) {
    if (!myNeedsRepaint) {
      if (oldValue != null) {
        setNeedsRepaint(!oldValue.equals(newValue));
      } else if (newValue != null) {
        setNeedsRepaint(!newValue.equals(oldValue));
      } else {
        setNeedsRepaint(false);
      }
    }

    return newValue;
  }

  @Nullable
  public <T> T setNeedsRepaint(T oldValue, T newValue, JComponent dirtyComponent) {
    if (!myNeedsRepaint) {
      if (oldValue != null) {
        setNeedsRepaint(!oldValue.equals(newValue));
      } else if (newValue != null) {
        setNeedsRepaint(!newValue.equals(oldValue));
      } else {
        setNeedsRepaint(false);
      }
    }

    return newValue;
  }

  protected void fireNeedsRepaint(JComponent dirtyComponent) {
    for (Listener each : myListeners) {
      each.onNeedsRepaint(this, dirtyComponent);
    }
  }

  public final void paint(final Component component, final Graphics2D g) {
    myNeedsRepaint = false;
    executePaint(component, g);
  }

  public abstract void executePaint(final Component component, final Graphics2D g);

}
