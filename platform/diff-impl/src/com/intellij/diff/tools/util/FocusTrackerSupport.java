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
package com.intellij.diff.tools.util;

import com.intellij.diff.DiffContext;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

public class FocusTrackerSupport<T> {
  public static class TwosideFocusTrackerSupport {
    @NotNull private Side myCurrentSide;

    private final boolean myDumbMode;
    @Nullable private final MyFocusListener myListener1;
    @Nullable private final MyFocusListener myListener2;

    public TwosideFocusTrackerSupport(@NotNull List<? extends EditorHolder> holders) {
      this(getComponent(holders.get(0)), getComponent(holders.get(1)));
      assert holders.size() == 2;
    }

    public TwosideFocusTrackerSupport(@Nullable JComponent component1, @Nullable JComponent component2) {
      assert component1 != null || component2 != null;
      myCurrentSide = component2 != null ? Side.RIGHT : Side.LEFT;

      myDumbMode = component1 == null || component2 == null;
      if (!myDumbMode) {
        myListener1 = new MyFocusListener(Side.LEFT);
        component1.addFocusListener(myListener1);

        myListener2 = new MyFocusListener(Side.RIGHT);
        component2.addFocusListener(myListener2);
      }
      else {
        myListener1 = null;
        myListener2 = null;
      }
    }

    @NotNull
    public Side getCurrentSide() {
      return myCurrentSide;
    }

    public void setCurrentSide(@NotNull Side side) {
      if (myDumbMode) return;
      myCurrentSide = side;
    }

    public void processContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
      Side side = context.getUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE);
      if (side != null) setCurrentSide(side);
    }

    public void updateContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
      if (myDumbMode) return;
      context.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE, myCurrentSide);
    }

    private class MyFocusListener extends FocusAdapter {
      @NotNull private final Side mySide;

      private MyFocusListener(@NotNull Side side) {
        mySide = side;
      }

      public void focusGained(FocusEvent e) {
        myCurrentSide = mySide;
      }
    }
  }

  public static class ThreesideFocusTrackerSupport {
    @NotNull private ThreeSide myCurrentSide;

    private final boolean myDumbMode;
    @Nullable private final MyFocusListener myListener1;
    @Nullable private final MyFocusListener myListener2;
    @Nullable private final MyFocusListener myListener3;

    public ThreesideFocusTrackerSupport(@NotNull List<? extends EditorHolder> editors) {
      this(getComponent(editors.get(0)), getComponent(editors.get(1)), getComponent(editors.get(2)));
    }

    public ThreesideFocusTrackerSupport(@Nullable JComponent component1, @Nullable JComponent component2, @Nullable JComponent component3) {
      assert component1 != null || component2 != null || component3 != null;
      myCurrentSide = component2 != null ? ThreeSide.BASE : component1 != null ? ThreeSide.LEFT : ThreeSide.RIGHT;

      boolean c1 = component1 != null;
      boolean c2 = component2 != null;
      boolean c3 = component3 != null;
      myDumbMode = (!c1 && !c2) || (!c1 && !c3) || (!c2 && !c3); // only one not-null element

      if (!myDumbMode) {
        myListener1 = component1 != null ? new MyFocusListener(ThreeSide.LEFT) : null;
        if (component1 != null) component1.addFocusListener(myListener1);

        myListener2 = component2 != null ? new MyFocusListener(ThreeSide.BASE) : null;
        if (component2 != null) component2.addFocusListener(myListener2);

        myListener3 = component3 != null ? new MyFocusListener(ThreeSide.RIGHT) : null;
        if (component3 != null) component3.addFocusListener(myListener3);
      }
      else {
        myListener1 = null;
        myListener2 = null;
        myListener3 = null;
      }
    }

    @NotNull
    public ThreeSide getCurrentSide() {
      return myCurrentSide;
    }

    public void setCurrentSide(@NotNull ThreeSide side) {
      if (myDumbMode || side.select(myListener1, myListener2, myListener3) == null) return;
      myCurrentSide = side;
    }

    public void processContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
      ThreeSide side = context.getUserData(DiffUserDataKeys.PREFERRED_FOCUS_THREESIDE);
      if (side != null) setCurrentSide(side);
    }

    public void updateContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
      if (myDumbMode) return;
      context.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_THREESIDE, myCurrentSide);
    }

    private class MyFocusListener extends FocusAdapter {
      @NotNull private final ThreeSide mySide;

      private MyFocusListener(@NotNull ThreeSide side) {
        mySide = side;
      }

      public void focusGained(FocusEvent e) {
        myCurrentSide = mySide;
      }
    }
  }

  @Nullable
  private static JComponent getComponent(@Nullable EditorHolder editor) {
    return editor != null ? editor.getFocusedComponent() : null;
  }
}
