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
package com.intellij.openapi.wm;

import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public abstract class FocusCommand extends ActiveRunnable implements Expirable {

  private Component myDominationComponent;
  private Throwable myAllocation;

  private ActionCallback myCallback;

  protected FocusCommand() {
  }

  protected FocusCommand(Component dominationComp) {
    myDominationComponent = dominationComp;
  }

  protected FocusCommand(final Object object) {
    super(object);
  }

  protected FocusCommand(final Object object, Component dominationComp) {
    super(object);
    myDominationComponent = dominationComp;
  }

  protected FocusCommand(final Object[] objects) {
    super(objects);
  }

  protected FocusCommand(final Object[] objects, Component dominationComp) {
    super(objects);
    myDominationComponent = dominationComp;
  }

  public final ActionCallback getCallback() {
    return myCallback;
  }

  public final void setCallback(ActionCallback callback) {
    myCallback = callback;
  }

  public boolean isExpired() {
    return false;
  }

  public boolean canExecuteOnInactiveApp() {
    return false;
  }

  @Nullable
  public KeyEventProcessor getProcessor() {
    return null;
  }

  @Nullable
  public final Component getDominationComponent() {
    return myDominationComponent;
  }

  public boolean dominatesOver(FocusCommand cmd) {
    final Component thisComponent = PopupUtil.getOwner(getDominationComponent());
    final Component thatComponent = PopupUtil.getOwner(cmd.getDominationComponent());

    if (thisComponent != null && thatComponent != null) {
      return thisComponent != thatComponent && SwingUtilities.isDescendingFrom(thisComponent, thatComponent);
    }

    return false;
  }

  public final FocusCommand saveAllocation() {
    if (Registry.is("ide.debugMode")) {
      myAllocation = new Exception();
    }
    return this;
  }

  public Throwable getAllocation() {
    return myAllocation;
  }

  @Override
  public String toString() {
    final Object[] objects = getEqualityObjects();
    return "FocusCommand objectCount=" + objects.length + " objects=" + Arrays.asList(objects);
  }

  public static class ByComponent extends FocusCommand {

    private Component myToFocus;

    public ByComponent(@Nullable Component toFocus) {
      this(toFocus, toFocus);
    }

    public ByComponent(@Nullable Component toFocus, @Nullable Component dominationComponent) {
      super(toFocus, dominationComponent);
      myToFocus = toFocus;
    }

    public final ActionCallback run() {
      if (myToFocus != null) {
        if (!myToFocus.requestFocusInWindow()) {
          myToFocus.requestFocus();
        }
      }
      return new ActionCallback.Done();
    }

    @Override
    public boolean isExpired() {
      return myToFocus == null || SwingUtilities.getWindowAncestor(myToFocus) == null;
    }

  }
}
