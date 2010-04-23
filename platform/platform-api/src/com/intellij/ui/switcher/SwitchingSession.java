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
package com.intellij.ui.switcher;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

public class SwitchingSession implements KeyEventDispatcher, Disposable {

  private SwitchProvider myProvider;
  private KeyEvent myInitialEvent;
  private boolean myFinished;
  private java.util.List<SwitchTarget> myTargets;
  private IdeGlassPane myGlassPane;

  private Map<SwitchTarget, TargetPainer> myPainters = new Hashtable<SwitchTarget, TargetPainer>();
  private JComponent myRootComponent;
  private SwitchTarget mySelection;

  public SwitchingSession(SwitchProvider provider, KeyEvent e) {
    myProvider = provider;
    myInitialEvent = e;

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);


    myTargets = myProvider.getTargets(true);

    Component eachParent = myProvider.getComponent();
    eachParent = eachParent.getParent();
    while (eachParent != null) {
      if (eachParent instanceof SwitchProvider) {
        SwitchProvider eachProvider = (SwitchProvider)eachParent;
        myTargets.addAll(eachProvider.getTargets(true));
        if (eachProvider.isCycleRoot()) {
          break;
        }
      }

      eachParent = eachParent.getParent();
    }



    if (myTargets.size() == 0) {
      Disposer.dispose(this);
      return;
    }


    mySelection = myProvider.getCurrentTarget();

    myGlassPane = IdeGlassPaneUtil.find(myProvider.getComponent());
    for (SwitchTarget each : myTargets) {
      TargetPainer eachPainter = new TargetPainer(each);
      Disposer.register(this, eachPainter);

      myRootComponent = myProvider.getComponent();
      myGlassPane.addPainter(each.getComponent(), eachPainter, this);
      myPainters.put(each, eachPainter);
    }

  }

  public boolean dispatchKeyEvent(KeyEvent e) {
    KeyEvent event = myInitialEvent;
    if ((e.getModifiers() & event.getModifiers()) == 0) {
      finish();
      return false;
    }

    return false;
  }

  private SwitchTarget getSelection() {
    return mySelection;
  }

  private class TargetPainer extends AbstractPainter implements Disposable {

    private SwitchTarget myTarget;

    private RelativePoint myPoint;

    private TargetPainer(SwitchTarget target) {
      myTarget = target;
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
      GraphicsConfig cfg = new GraphicsConfig(g);
      cfg.setAntialiasing(true);

      g.setColor(Color.red);
      Rectangle paintRect = myTarget.getRectangle().getRectangleOn(component);

      boolean selected = myTarget.equals(getSelection());
      if (selected) {
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[] {2, 4}, 0));
        g.draw(paintRect);
      } else {
        g.setColor(Color.red);
        int d = 6;
        int dX = 4;
        int dY = -4;
        g.fillOval(paintRect.x + dX - d / 2, paintRect.y + paintRect.height + dY - d / 2, d, d);
      }

      if (myPoint != null) {
        g.setColor(Color.green);
        Point p = myPoint.getPoint(component);
        //g.fillOval(p.x - 2, p.y - 2, 4, 4);
      }

      cfg.restore();
    }

    public void setPoint(RelativePoint point) {
      myPoint = point;
    }

    @Override
    public boolean needsRepaint() {
      return true;
    }

    public void dispose() {
      myGlassPane.removePainter(this);
    }
  }

  private enum Direction {
    up, down, left, right
  }

  public void up() {
    setSelection(getNextTarget(Direction.up));
  }

  public void down() {
    setSelection(getNextTarget(Direction.down));
  }

  public void left() {
    setSelection(getNextTarget(Direction.left));
  }

  public void right() {
    setSelection(getNextTarget(Direction.right));
  }

  private void setSelection(SwitchTarget target) {
    mySelection = target;

    for (TargetPainer each : myPainters.values()) {
      each.setNeedsRepaint(true);
    }
  }

  private SwitchTarget getNextTarget(Direction direction) {
    if (myTargets.size() == 1) {
      return getSelection();
    }

    List<Point> points = new ArrayList<Point>();
    Point selected = null;
    Map<SwitchTarget, Point> target2Point = new HashMap<SwitchTarget, Point>();
    for (SwitchTarget each : myTargets) {
      Rectangle eachRec = each.getRectangle().getRectangleOn(myRootComponent);
      Point eachPoint = null;
      switch (direction) {
        case up:
          eachPoint = new Point(eachRec.x + eachRec.width / 2, eachRec.y + eachRec.height);
          break;
        case down:
          eachPoint = new Point(eachRec.x + eachRec.width /2, eachRec.y);
          break;
        case left:
          eachPoint = new Point(eachRec.x + eachRec.width, eachRec.y + eachRec.height / 2);
          break;
        case right:
          eachPoint = new Point(eachRec.x, eachRec.y + eachRec.height / 2);
          break;
      }

      if (each.equals(mySelection)) {
        switch (direction) {
          case up:
            selected = new Point(eachRec.x + eachRec.width / 2, eachRec.y);
            break;
          case down:
            selected = new Point(eachRec.x + eachRec.width / 2, eachRec.y + eachRec.height);
            break;
          case left:
            selected = new Point(eachRec.x, eachRec.y + eachRec.height / 2);
            break;
          case right:
            selected = new Point(eachRec.x + eachRec.width, eachRec.y + eachRec.height / 2);
            break;
        }
        points.add(selected);
        target2Point.put(each, selected);

        myPainters.get(each).setPoint(new RelativePoint(myRootComponent, selected));
      } else {
        points.add(eachPoint);
        target2Point.put(each, eachPoint);

        myPainters.get(each).setPoint(new RelativePoint(myRootComponent, eachPoint));
      }
    }

    TreeMap<Integer, SwitchTarget> distance = new TreeMap<Integer, SwitchTarget>();
    for (SwitchTarget eachTarget : myTargets) {
      Point eachPoint = target2Point.get(eachTarget);
      if (selected == eachPoint) continue;

      double eachDistance = sqrt(abs(eachPoint.getX() - selected.getX())) + sqrt(abs(eachPoint.getY() - selected.getY()));
      distance.put((int)eachDistance, eachTarget);
    }


    Integer[] distancesArray = distance.keySet().toArray(new Integer[distance.size()]);
    for (Integer eachDistance : distancesArray) {
      SwitchTarget eachTarget = distance.get(eachDistance);
      Point eachPoint = target2Point.get(eachTarget);
      switch (direction) {
        case up:
          if (eachPoint.y <= selected.y) {
            return eachTarget;
          }
          break;
        case down:
          if (eachPoint.y >= selected.y) {
            return eachTarget;
          }
          break;
        case left:
          if (eachPoint.x <= selected.x) {
            return eachTarget;
          }
          break;
        case right:
          if (eachPoint.x >= selected.x) {
            return eachTarget;
          }
          break;
      }
    }

    for (int i = distancesArray.length - 1; i >= 0; i--) {
      SwitchTarget eachTarget = distance.get(distancesArray[i]);
      Point eachPoint = target2Point.get(eachTarget);

      switch (direction) {
        case up:
          if (eachPoint.y >= selected.y) {
            return eachTarget;
          }
          break;
        case down:
          if (eachPoint.y <= selected.y) {
            return eachTarget;
          }
          break;
        case left:
          if (eachPoint.x >= selected.x) {
            return eachTarget;
          }
          break;
        case right:
          if (eachPoint.x <= selected.x) {
            return eachTarget;
          }
          break;
      }
    }

    return distance.values().iterator().next();
  }

  private void selectNext() {
    int index = myTargets.indexOf(getSelection());
    if (index + 1 < myTargets.size()) {
      mySelection = myTargets.get(index + 1);
    } else {
      mySelection = myTargets.get(0);
    }

    for (TargetPainer each : myPainters.values()) {
      each.setNeedsRepaint(true);
    }
  }

  public void dispose() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    myFinished = true;
  }

  private void finish() {
    SwitchTarget selection = getSelection();
    if (selection != null) {
      selection.switchTo(true);
    }
    Disposer.dispose(this);
  }

  public boolean isFinished() {
    return myFinished;
  }
}
