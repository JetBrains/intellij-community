/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBCardLayout;

import java.awt.*;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class JBSlidingPanel extends JBPanel {
  private final ArrayList<Pair<String,Component>> mySlides = new ArrayList<Pair<String, Component>>();
  private int mySelectedIndex = -1;

  public JBSlidingPanel() {
    setLayout(new JBCardLayout());
  }

  @Override
  public JBCardLayout getLayout() {
    return (JBCardLayout)super.getLayout();
  }

  @Override
  public Component add(String name, Component comp) {
    mySlides.add(Pair.create(name, comp));
    if (mySelectedIndex == -1) {
      mySelectedIndex = 0;
    }
    return super.add(name, comp);
  }

  public ActionCallback goLeft() {
    if (mySelectedIndex == 0) {
      return ActionCallback.REJECTED;
    }
    mySelectedIndex--;
    return applySlide(JBCardLayout.SwipeDirection.BACKWARD);
  }

  public ActionCallback swipe(String id, JBCardLayout.SwipeDirection direction) {
    final ActionCallback done = new ActionCallback();
    getLayout().swipe(this, id, direction, new Runnable() {
      @Override
      public void run() {
        done.setDone();
      }
    });
    return done;
  }

  public ActionCallback goRight() {
    if (mySelectedIndex == mySlides.size() - 1) {
      return ActionCallback.REJECTED;
    }
    mySelectedIndex++;
    return applySlide(JBCardLayout.SwipeDirection.FORWARD);
  }

  private ActionCallback applySlide(JBCardLayout.SwipeDirection direction) {
    final ActionCallback callback = new ActionCallback();
    getLayout().swipe(this, mySlides.get(mySelectedIndex).first, direction, new Runnable() {
      @Override
      public void run() {
        callback.setDone();
      }
    });
    return callback;
  }

  @Override
  @Deprecated
  public Component add(Component comp) {
    throw new AddMethodIsNotSupportedException();
  }

  @Override
  @Deprecated
  public Component add(Component comp, int index) {
    throw new AddMethodIsNotSupportedException();
  }

  @Override
  @Deprecated
  public void add(Component comp, Object constraints) {
    throw new AddMethodIsNotSupportedException();
  }

  @Override
  @Deprecated
  public void add(Component comp, Object constraints, int index) {
    throw new AddMethodIsNotSupportedException();
  }

  private static class AddMethodIsNotSupportedException extends RuntimeException {
    public AddMethodIsNotSupportedException() {
      super("Use add(String, Component) method");
    }
  }
}
