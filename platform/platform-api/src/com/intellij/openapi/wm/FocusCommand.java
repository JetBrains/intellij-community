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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * The container class for focus requests for <code>IdeFocusManager</code>
 * @see IdeFocusManager
 */
public abstract class FocusCommand extends ActiveRunnable implements Expirable {
  protected Component myDominationComponent;
  private Throwable myAllocation;
  private ActionCallback myCallback;
  private boolean myInvalidatesPendingFurtherRequestors = true;
  private Expirable myExpirable;

  protected FocusCommand() {
    saveAllocation();
  }

  protected FocusCommand(Component dominationComp) {
    myDominationComponent = dominationComp;
    saveAllocation();
  }

  protected FocusCommand(final Object object) {
    super(object);
    saveAllocation();
  }

  protected FocusCommand(final Object object, Component dominationComp) {
    super(object);
    myDominationComponent = dominationComp;
    saveAllocation();
  }

  protected FocusCommand(final Object[] objects) {
    super(objects);
    saveAllocation();
  }

  protected FocusCommand(final Object[] objects, Component dominationComp) {
    super(objects);
    myDominationComponent = dominationComp;
    saveAllocation();
  }

  public final ActionCallback getCallback() {
    return myCallback;
  }

  public final void setCallback(ActionCallback callback) {
    myCallback = callback;
  }

  public boolean isExpired() {
    return myExpirable != null && myExpirable.isExpired();
  }

  public boolean canExecuteOnInactiveApp() {
    return false;
  }

  @Nullable
  public KeyEventProcessor getProcessor() {
    return null;
  }

  public boolean invalidatesRequestors() {
    return myInvalidatesPendingFurtherRequestors;
  }

  public FocusCommand setExpirable(Expirable expirable) {
    myExpirable = expirable;
    return this;
  }

  public FocusCommand setToInvalidateRequestors(boolean invalidatesPendingFurtherRequestors) {
    myInvalidatesPendingFurtherRequestors = invalidatesPendingFurtherRequestors;
    return this;
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
    myAllocation = new Exception();
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

    @NotNull
    public final ActionCallback run() {
      if (myToFocus != null) {
        if (!myToFocus.requestFocusInWindow()) {
          myToFocus.requestFocus();
        }
      }
      clear();
      return new ActionCallback.Done();
    }

    private void clear() {
      myToFocus = null;
      myDominationComponent = null;
    }

    @Override
    public boolean isExpired() {
      if (myToFocus == null) {
        return true;
      }
      if (SwingUtilities.getWindowAncestor(myToFocus) == null) {
        clear();
        return true;
      }
      return false;
    }

    public Component getComponent() {
      return myToFocus;
    }
  }
}
