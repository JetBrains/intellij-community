// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util;

import com.intellij.diff.DiffContext;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

@ApiStatus.Internal
public abstract class FocusTrackerSupport<S> {
  @NotNull
  public abstract S getCurrentSide();

  public abstract void setCurrentSide(@NotNull S side);

  public abstract void processContextHints(@NotNull DiffRequest request, @NotNull DiffContext context);

  public abstract void updateContextHints(@NotNull DiffRequest request, @NotNull DiffContext context);

  //
  // Impl
  //

  public static class Twoside extends FocusTrackerSupport<Side> {
    @NotNull private Side myCurrentSide;

    public Twoside(@NotNull List<? extends EditorHolder> holders) {
      assert holders.size() == 2;

      myCurrentSide = Side.RIGHT;

      addListener(holders, Side.LEFT);
      addListener(holders, Side.RIGHT);
    }

    @Override
    @NotNull
    public Side getCurrentSide() {
      return myCurrentSide;
    }

    @Override
    public void setCurrentSide(@NotNull Side side) {
      myCurrentSide = side;
    }

    @Override
    public void processContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
      Side side = DiffUtil.getUserData(request, context, DiffUserDataKeys.PREFERRED_FOCUS_SIDE);
      if (side != null) setCurrentSide(side);
    }

    @Override
    public void updateContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
      context.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE, myCurrentSide);
    }

    private void addListener(@NotNull List<? extends EditorHolder> holders, @NotNull Side side) {
      side.select(holders).installFocusListener(new MyFocusListener(side));
    }

    private final class MyFocusListener extends FocusAdapter {
      @NotNull private final Side mySide;

      private MyFocusListener(@NotNull Side side) {
        mySide = side;
      }

      @Override
      public void focusGained(FocusEvent e) {
        myCurrentSide = mySide;
      }
    }
  }

  public static class Threeside extends FocusTrackerSupport<ThreeSide> {
    @NotNull private ThreeSide myCurrentSide;

    public Threeside(@NotNull List<? extends EditorHolder> holders) {
      myCurrentSide = ThreeSide.BASE;

      addListener(holders, ThreeSide.LEFT);
      addListener(holders, ThreeSide.BASE);
      addListener(holders, ThreeSide.RIGHT);
    }

    @Override
    @NotNull
    public ThreeSide getCurrentSide() {
      return myCurrentSide;
    }

    @Override
    public void setCurrentSide(@NotNull ThreeSide side) {
      myCurrentSide = side;
    }

    @Override
    public void processContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
      ThreeSide side = DiffUtil.getUserData(request, context, DiffUserDataKeys.PREFERRED_FOCUS_THREESIDE);
      if (side != null) setCurrentSide(side);
    }

    @Override
    public void updateContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
      context.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_THREESIDE, myCurrentSide);
    }

    private void addListener(@NotNull List<? extends EditorHolder> holders, @NotNull ThreeSide side) {
      side.select(holders).installFocusListener(new MyFocusListener(side));
    }

    private final class MyFocusListener extends FocusAdapter {
      @NotNull private final ThreeSide mySide;

      private MyFocusListener(@NotNull ThreeSide side) {
        mySide = side;
      }

      @Override
      public void focusGained(FocusEvent e) {
        myCurrentSide = mySide;
      }
    }
  }
}
