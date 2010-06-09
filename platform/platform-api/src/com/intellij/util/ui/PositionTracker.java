/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.awt.RelativePoint;

import java.awt.*;
import java.awt.event.*;

public abstract class PositionTracker<T> implements Disposable, HierarchyBoundsListener, HierarchyListener, ComponentListener {

  private Component myComponent;
  private Client<T> myClient;

  public PositionTracker(Component component) {
    myComponent = component;
  }

  public final void init(Client<T> client) {
    myClient = client;

    Disposer.register(client, this);

    myComponent.addHierarchyBoundsListener(this);
    myComponent.addHierarchyListener(this);
    myComponent.addComponentListener(this);
  }

  public final Component getComponent() {
    return myComponent;
  }

  public final void ancestorMoved(HierarchyEvent e) {
    revalidate();
  }

  public final void ancestorResized(HierarchyEvent e) {
    revalidate();
  }

  public final void hierarchyChanged(HierarchyEvent e) {
    revalidate();
  }

  public void componentResized(ComponentEvent e) {
    revalidate();
  }

  public void componentMoved(ComponentEvent e) {
    revalidate();
  }

  public void componentShown(ComponentEvent e) {
    revalidate();
  }

  public void componentHidden(ComponentEvent e) {
    revalidate();
  }

  protected final void revalidate() {
    myClient.revalidate(this);
  }

  public abstract RelativePoint recalculateLocation(T object);

  public final void dispose() {
    myComponent.removeHierarchyBoundsListener(this);
    myComponent.removeHierarchyListener(this);
    myComponent.removeComponentListener(this);
  }

  public static final class Static<T> extends PositionTracker<T> {

    private RelativePoint myPoint;

    public Static(RelativePoint point) {
      super(point.getComponent());
      myPoint = point;
    }

    @Override
    public RelativePoint recalculateLocation(Object object) {
      return myPoint;
    }
  }

  public interface Client<T> extends Disposable {
    
    void revalidate(PositionTracker<T> tracker);

  }

}
