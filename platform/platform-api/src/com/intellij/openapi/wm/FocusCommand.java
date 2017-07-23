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
package com.intellij.openapi.wm;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;

/**
 * The container class for focus requests for {@code IdeFocusManager}
 * @see IdeFocusManager
 */
public abstract class FocusCommand extends ActiveRunnable implements Expirable {
  protected Component myDominationComponent;
  private Throwable myAllocation;
  private ActionCallback myCallback;
  private boolean myInvalidatesPendingFurtherRequestors = true;
  private Expirable myExpirable;

  private static long lastProcessedCommandTime = 0;
  protected final long commandCreationTime = System.currentTimeMillis();

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.FocusCommand");

  public boolean isForced() {
    return myForced;
  }

  public void setForced(boolean forced) {
    myForced = forced;
  }

  private boolean myForced;

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

  public boolean canFocusChangeFrom(@Nullable Component component) {
    return true;
  }

  @Override
  public String toString() {
    final Object[] objects = getEqualityObjects();
    return "FocusCommand objectCount=" + objects.length + " objects=" + Arrays.asList(objects);
  }

  public static class ByComponent extends FocusCommand {
    private Component myToFocus;
    @Nullable private final Project myProject;
    private Throwable myAllocation;

    public ByComponent(@Nullable Component toFocus, @NotNull Throwable allocation) {
      this(toFocus, toFocus, null, allocation);
    }

    public ByComponent(@Nullable Component toFocus, @Nullable Component dominationComponent, @Nullable Project project, @NotNull Throwable allocation) {
      super(toFocus, dominationComponent);
      myProject = project;
      myAllocation = allocation;
      myToFocus = toFocus;
    }

    @NotNull
    public final ActionCallback run() {

      boolean shouldLogFocuses = Registry.is("ide.log.focuses");

      if (shouldLogFocuses) {
        myToFocus.addFocusListener(new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            if (isExpired()) return;
            super.focusGained(e);
            LOG.info("Focus gained on " + myToFocus.getClass().getName());
            myToFocus.removeFocusListener(this);
          }
        });
      }


      if (commandCreationTime > lastProcessedCommandTime) {
        if (!(myToFocus.requestFocusInWindow())) {
          if (shouldLogFocuses) {
            LOG.info("We could not request focus in window on " + myToFocus.getClass().getName());
            LOG.info(myAllocation);
          }
          if (ApplicationManager.getApplication().isActive()) {
            IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
            if (frame == null || myProject == null || myProject == frame.getProject()) {
              myToFocus.requestFocus();
              if (shouldLogFocuses) {
                LOG.info("Force request focus on " + myToFocus.getClass().getName());
              }
            }
          }
        }
        else if (shouldLogFocuses) {
          LOG.info("We have successfully requested focus in window on " + myToFocus.getClass().getName());
          LOG.info(myAllocation);
        }
        lastProcessedCommandTime = commandCreationTime;
      }

      clear();
      return ActionCallback.DONE;
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
      return false;
    }

    public Component getComponent() {
      return myToFocus;
    }

    @Override
    public boolean canFocusChangeFrom(@Nullable Component component) {
      DialogWrapper dialog = DialogWrapper.findInstance(component);
      return (dialog == null) || (dialog == DialogWrapper.findInstance(myToFocus));
    }
  }
}
