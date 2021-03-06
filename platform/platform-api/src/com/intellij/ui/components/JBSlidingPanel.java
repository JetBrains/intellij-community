// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBCardLayout;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class JBSlidingPanel extends JBPanel {
  private final ArrayList<Pair<String, Component>> mySlides = new ArrayList<>();
  private int mySelectedIndex = -1;

  public JBSlidingPanel() {
    setLayout(new JBCardLayout());
  }

  @Override
  public JBCardLayout getLayout() {
    return (JBCardLayout)super.getLayout();
  }

  @Override
  public Component add(@NonNls String name, Component comp) {
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
    getLayout().swipe(this, id, direction, () -> done.setDone());
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
    getLayout().swipe(this, mySlides.get(mySelectedIndex).first, direction, () -> callback.setDone());
    return callback;
  }

  /**
   * @deprecated MUST use {@link #add(String, Component)}
   */
  @Override
  @Deprecated
  public Component add(Component comp) {
    throw new AddMethodIsNotSupportedException();
  }

  /**
   * @deprecated MUST use {@link #add(String, Component)}
   */
  @Override
  @Deprecated
  public Component add(Component comp, int index) {
    throw new AddMethodIsNotSupportedException();
  }

  /**
   * @deprecated MUST use {@link #add(String, Component)}
   */
  @Override
  @Deprecated
  public void add(Component comp, Object constraints) {
    throw new AddMethodIsNotSupportedException();
  }

  /**
   * @deprecated MUST use {@link #add(String, Component)}
   */
  @Override
  @Deprecated
  public void add(Component comp, Object constraints, int index) {
    throw new AddMethodIsNotSupportedException();
  }

  private static class AddMethodIsNotSupportedException extends RuntimeException {
    AddMethodIsNotSupportedException() {
      super("Use add(String, Component) method");
    }
  }
}
