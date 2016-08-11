/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

public class SwitchingSession implements KeyEventDispatcher, Disposable {

  private SwitchProvider myProvider;
  private KeyEvent myInitialEvent;
  private boolean myFinished;
  private LinkedHashSet<SwitchTarget> myTargets = new LinkedHashSet<>();
  private IdeGlassPane myGlassPane;

  private Component myRootComponent;

  private SwitchTarget mySelection;
  private SwitchTarget myStartSelection;

  private boolean mySelectionWasMoved;

  private Alarm myAlarm = new Alarm();
  private Runnable myAutoApplyRunnable = new Runnable() {
    public void run() {
      if (myManager.canApplySwitch()) {
        myManager.applySwitch();
      }
    }
  };
  private SwitchManager myManager;
  private Spotlight mySpotlight;

  private boolean myShowspots;
  private Alarm myShowspotsAlarm;
  private Runnable myShowspotsRunnable = new Runnable() {
    public void run() {
      if (!myShowspots) {
        setShowspots(true);
      }
    }
  };

  private boolean myFadingAway;
  private Disposable myPainterDisposable = Disposer.newDisposable();

  public SwitchingSession(SwitchManager mgr, SwitchProvider provider, KeyEvent e, @Nullable SwitchTarget preselected, boolean showSpots) {
    myManager = mgr;
    myProvider = provider;
    myInitialEvent = e;

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);


    myTargets.addAll(myProvider.getTargets(true, true));

    Component eachParent = myProvider.getComponent();
    eachParent = eachParent.getParent();
    while (eachParent != null) {
      if (eachParent instanceof SwitchProvider) {
        SwitchProvider eachProvider = (SwitchProvider)eachParent;
        myTargets.addAll(eachProvider.getTargets(true, false));
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
    if (myTargets.contains(preselected)) {
      mySelection = preselected;
    }

    myStartSelection = mySelection;

    myGlassPane = IdeGlassPaneUtil.find(myProvider.getComponent());
    myRootComponent = myProvider.getComponent().getRootPane();
    mySpotlight = new Spotlight(myRootComponent);
    myGlassPane.addPainter(myRootComponent, mySpotlight, myPainterDisposable);

    myShowspotsAlarm = new Alarm(this);
    restartShowspotsAlarm();

    myShowspots = showSpots;
    mySpotlight.setNeedsRepaint(true);
   }

  public void setFadeaway(boolean fadeAway) {
    myFadingAway = fadeAway;
  }


  private class Spotlight extends AbstractPainter {

    private Component myRoot;

    private Area myArea;

    private BufferedImage myBackground;

    private Spotlight(Component root) {
      myRoot = root;
    }

    @Override
    public boolean needsRepaint() {
      return true;
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
      int inset = -1;
      int selectedInset = -8;

      Set<Area> shapes = new HashSet<>();
      Area selected = null;

      boolean hasIntersections = false;

      Rectangle clip = g.getClipBounds();
      myArea = new Area(clip);

      for (SwitchTarget each : myTargets) {
        RelativeRectangle eachSimpleRec = each.getRectangle();
        if (eachSimpleRec == null) continue;

        boolean isSelected = each.equals(mySelection);

        Rectangle eachBaseRec = eachSimpleRec.getRectangleOn(myRoot);
        Rectangle eachShape;
        if (isSelected) {
          eachShape = new Rectangle(eachBaseRec.x + selectedInset,
                                    eachBaseRec.y + selectedInset,
                                    eachBaseRec.width - selectedInset -selectedInset,
                                    eachBaseRec.height - selectedInset -selectedInset);
        } else {
          eachShape = new Rectangle(eachBaseRec.x + inset,
                                    eachBaseRec.y + inset,
                                    eachBaseRec.width - inset -inset,
                                    eachBaseRec.height - inset -inset);
        }

        if (!hasIntersections) {
          hasIntersections = clip.contains(eachShape) || clip.intersects(eachShape);
        }

        Area eachArea = new Area(new RoundRectangle2D.Double(eachShape.x, eachShape.y, eachShape.width, eachShape.height, 6, 6));
        shapes.add(eachArea);
        if (isSelected) {
          selected = eachArea;
        }
      }


      Color fillColor = new Color(0f, 0f, 0f, 0.25f);
      if (!hasIntersections && myShowspots) {
        g.setColor(fillColor);
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        return;
      }

      for (Area each : shapes) {
        myArea.subtract(each);
        if (each != selected) {
          each.subtract(selected);
        }
      }

      GraphicsConfig cfg = new GraphicsConfig(g);
      cfg.setAntialiasing(true);

      if (myShowspots) {
        g.setColor(fillColor);
        g.fill(myArea);

        g.setColor(Color.lightGray);
        for (Shape each : shapes) {
          if (each != selected) {
            g.draw(each);
          }
        }
      }

      if (selected != null) {
        Color bg = Color.darkGray;
        g.setColor(ColorUtil.toAlpha(bg, 180));
        g.setStroke(new BasicStroke(3));
        g.draw(selected);
      }

      cfg.restore();
    }
  }

  public boolean dispatchKeyEvent(KeyEvent e) {
    if (myFadingAway) {
      _dispose();
    } else {
      KeyEvent event = myInitialEvent;
      if (event == null || ((e.getModifiers() & event.getModifiers()) == 0)) {
        finish(!isSelectionWasMoved());
        return false;
      }
    }

    return false;
  }

  private SwitchTarget getSelection() {
    return mySelection;
  }

  public boolean isSelectionWasMoved() {
    return mySelectionWasMoved;
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
    if (target == null) return;

    mySelection = target;

    mySelectionWasMoved |= !mySelection.equals(myStartSelection);

    mySpotlight.setNeedsRepaint(true);

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myAutoApplyRunnable, Registry.intValue("actionSystem.autoSelectTimeout"));

    restartShowspotsAlarm();
  }

  private SwitchTarget getNextTarget(Direction direction) {
    if (myTargets.size() == 1) {
      return getSelection();
    }

    List<Point> points = new ArrayList<>();
    Point selected = null;
    Map<SwitchTarget, Point> target2Point = new HashMap<>();
    for (SwitchTarget each : myTargets) {
      RelativeRectangle rectangle = each.getRectangle();
      if (rectangle == null) continue;
      Rectangle eachRec = rectangle.getRectangleOn(myRootComponent);
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
      }
      else {
        points.add(eachPoint);
        target2Point.put(each, eachPoint);
      }
    }

    TreeMap<Integer, SwitchTarget> distance = new TreeMap<>();
    for (SwitchTarget eachTarget : myTargets) {
      Point eachPoint = target2Point.get(eachTarget);
      if (eachPoint == null || selected == null) continue;
      if (selected == eachPoint) continue;

      double eachDistance = sqrt(abs(eachPoint.getX() - selected.getX())) + sqrt(abs(eachPoint.getY() - selected.getY()));
      distance.put((int)eachDistance, eachTarget);
    }


    Integer[] distancesArray = distance.keySet().toArray(new Integer[distance.size()]);
    for (Integer eachDistance : distancesArray) {
      SwitchTarget eachTarget = distance.get(eachDistance);
      Point eachPoint = target2Point.get(eachTarget);
      if (eachPoint == null || selected == null) continue;
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
      if (eachPoint == null || selected == null) continue;
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


    if (myTargets.size() == 0) return null;

    List<SwitchTarget> all = Arrays.asList(myTargets.toArray(new SwitchTarget[myTargets.size()]));
    int index = all.indexOf(getSelection());
    if (index + 1 < myTargets.size()) {
      return all.get(index + 1);
    }
    else {
      return all.get(0);
    }
  }

  public void dispose() {
    myFinished = true;

    if (myFadingAway) {
      myManager.addFadingAway(this);
      myAlarm.addRequest(() -> _dispose(), Registry.intValue("actionSystem.keyGestureDblClickTime"));
    } else {
      _dispose();
    }
  }

  private void _dispose() {
    myFadingAway = false;
    myManager.removeFadingAway(this);
    Disposer.dispose(myPainterDisposable);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
  }

  public AsyncResult<SwitchTarget> finish(final boolean fadeAway) {
    myAlarm.cancelAllRequests();

    final AsyncResult<SwitchTarget> result = new AsyncResult<>();
    final SwitchTarget selection = getSelection();
    if (selection != null) {
      selection.switchTo(true).doWhenDone(() -> {
        myManager.disposeCurrentSession(fadeAway);
        result.setDone(selection);
      }).notifyWhenRejected(result);
    } else {
      Disposer.dispose(this);
      result.setDone();
    }

    return result;
  }

  public boolean isFinished() {
    return myFinished;
  }

  public void setShowspots(boolean showspots) {
    if (myShowspots != showspots) {
      myShowspots = showspots;
      mySpotlight.setNeedsRepaint(true);
    }
  }

  public boolean isShowspots() {
    return myShowspots;
  }
  
  private void restartShowspotsAlarm() {
    myShowspotsAlarm.cancelAllRequests();
    myShowspotsAlarm.addRequest(myShowspotsRunnable, Registry.intValue("actionSystem.quickAccessShowSpotsTime"));
  }
}
