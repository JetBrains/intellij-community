// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A simple interpolator that relies on Swing {@link Timer}.
 * <p>
 * Currently, it performs linear interpolation but we may add support
 * for custom curves and ease in / ease out behavior.
 */
public class Interpolator {
  private static final int PERIOD = 7; // ms
  private static final long TIMEOUT = 500_000_000L; // ns

  private final Supplier<Integer> myInput;
  private final Consumer<? super Integer> myOutput;

  private final Timer myTimer = new Timer(PERIOD, new TimerListener());
  private final Deque<Segment> mySegments = new ArrayDeque<>();

  private boolean myProcessEvents = true;
  private long myPreviousEventMoment = 0;

  /**
   * Creates a new instance of {@link Interpolator}.
   *
   * @param input  a getter of the value
   * @param output a setter of the value
   */
  public Interpolator(@NotNull Supplier<Integer> input, @NotNull Consumer<? super Integer> output) {
    myInput = input;
    myOutput = output;
  }

  /**
   * Adds a new target value.
   *
   * @param value target value
   * @param delay initial delay
   */
  public void setTarget(int value, int delay) {
    if (!myProcessEvents) {
      return;
    }

    if (Objects.equals(getTarget(), value)) {
      return;
    }

    long moment = System.nanoTime();
    long elapsed = (moment - myPreviousEventMoment);

    if (mySegments.isEmpty()) {
      mySegments.add(new Line(new Position(moment, myInput.get()),
                              new Position(moment + delay * 1000000L, value)));
    }
    else {
      Segment segment = mySegments.getLast();
      mySegments.add(new Line(new Position(segment.getEnd().getMoment(), segment.getEnd().getValue()),
                              new Position(segment.getEnd().getMoment() + elapsed, value)));
    }

    myPreviousEventMoment = moment;

    if (!myTimer.isRunning()) {
      myTimer.start();
    }
  }

  /**
   * Gets the ultimate target value (or the current value if there's no target).
   *
   * @return the ultimate target value or the current value if there's no target
   */
  public int getTarget() {
    return mySegments.isEmpty() ? myInput.get() : mySegments.getLast().getEnd().getValue();
  }

  private class TimerListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent event) {
      long moment = System.nanoTime();

      Segment segment = null;

      while (!mySegments.isEmpty()) {
        segment = mySegments.getFirst();

        if (segment.getEnd().getMoment() < moment) {
          mySegments.removeFirst();
          continue;
        }

        Integer v = segment.getValueAt(moment);

        if (v != null && !Objects.equals(v, myInput.get())) {
          setValue(v);
        }

        break;
      }

      if (mySegments.isEmpty() && segment != null) {
        setValue(segment.getEnd().getValue());
      }

      if (moment > myPreviousEventMoment + TIMEOUT) {
        myTimer.stop();
      }
    }

    private void setValue(Integer v) {
      myProcessEvents = false;
      myOutput.accept(v);
      myProcessEvents = true;
    }
  }

  private static abstract class Segment {
    protected final Position myBegin;
    protected final Position myEnd;

    private Segment(Position begin, Position end) {
      myBegin = begin;
      myEnd = end;
    }

    Position getBegin() {
      return myBegin;
    }

    Position getEnd() {
      return myEnd;
    }

    long getDuration() {
      return myEnd.getMoment() - myBegin.getMoment();
    }

    int getDelta() {
      return myEnd.getValue() - myBegin.getValue();
    }

    Integer getValueAt(long moment) {
      return contains(moment) ? get(moment) : null;
    }

    boolean contains(long moment) {
      return myBegin.getMoment() <= moment && moment <= myEnd.getMoment();
    }

    protected abstract Integer get(long moment);
  }

  private static final class Line extends Segment {
    private Line(Position begin, Position end) {
      super(begin, end);
    }

    @Override
    protected Integer get(long moment) {
      int range = myEnd.getValue() - myBegin.getValue();
      long duration = myEnd.getMoment() - myBegin.getMoment();
      long elapsed = moment - myBegin.getMoment();
      double fraction = (double)elapsed / duration;
      return (int)Math.round((double)myBegin.getValue() + fraction * range);
    }
  }

  private static final class Position {
    private final long myMoment;
    private final int myValue;

    private Position(long moment, int value) {
      myMoment = moment;
      myValue = value;
    }

    long getMoment() {
      return myMoment;
    }

    int getValue() {
      return myValue;
    }
  }
}
