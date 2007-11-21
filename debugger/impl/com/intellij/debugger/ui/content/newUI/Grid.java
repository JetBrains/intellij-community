package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.*;

class Grid extends Wrapper implements Disposable, CellTransform.Facade {
  private DebuggerSettings mySettings;

  private ThreeComponentsSplitter myTopSplit = new ThreeComponentsSplitter();
  private Splitter mySplitter = new Splitter(true);

  private HashMap<PlaceInGrid, GridCell> myPlaceInGrid2Cell = new HashMap<PlaceInGrid, GridCell>();
  ActionManager myActionManager;

  private Placeholder myLeft = new Placeholder();
  private Placeholder myCenter = new Placeholder();
  private Placeholder myRight = new Placeholder();
  private Placeholder myBottom = new Placeholder();

  private String mySessionName;

  private List<Content> myContents = new ArrayList<Content>();
  private Map<Content, GridCell> myContent2Cell = new java.util.HashMap<Content, GridCell>();

  private Comparator<Content> myContentComparator = new Comparator<Content>() {
    public int compare(final Content o1, final Content o2) {
      return getCellFor(o1).getPlaceInGrid().compareTo(getCellFor(o2).getPlaceInGrid());
    }
  };
  ContentManager myContentManager;

  private NewDebuggerContentUI myUI;

  public Grid(NewDebuggerContentUI ui, String sessionName, boolean horizontalToolbars) {
    myUI = ui;

    Disposer.register(myUI, this);

    myContentManager = ui.myManager;
    mySettings = ui.mySettings;
    myActionManager = ui.myActionManager;
    mySessionName = sessionName;

    setOpaque(false);

    myPlaceInGrid2Cell.put(PlaceInGrid.left, new GridCell(this, myUI.myProject, myLeft, horizontalToolbars, PlaceInGrid.left));
    myPlaceInGrid2Cell.put(PlaceInGrid.center, new GridCell(this, myUI.myProject, myCenter, horizontalToolbars, PlaceInGrid.center));
    myPlaceInGrid2Cell.put(PlaceInGrid.right, new GridCell(this, myUI.myProject, myRight, horizontalToolbars, PlaceInGrid.right));
    myPlaceInGrid2Cell.put(PlaceInGrid.bottom, new GridCell(this, myUI.myProject, myBottom, horizontalToolbars, PlaceInGrid.bottom));

    setContent(mySplitter);

    myTopSplit.setFirstComponent(myLeft);
    myTopSplit.setInnerComponent(myCenter);
    myTopSplit.setLastComponent(myRight);
    mySplitter.setFirstComponent(myTopSplit);
    mySplitter.setSecondComponent(myBottom);

    setFocusCycleRoot(true);
  }

  public void addNotify() {
    super.addNotify();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        restoreProportions();
      }
    });

    updateSelection(true);
  }

  public void removeNotify() {
    super.removeNotify();

    updateSelection(false);
  }

  private void updateSelection(boolean isShowing) {
    for (GridCell each: myPlaceInGrid2Cell.values()) {
      each.updateSelection(isShowing);     
    }
  }

  private void restoreProportions() {
    for (final GridCell cell : myPlaceInGrid2Cell.values()) {
      cell.restoreProportion();
    }
  }

  public void add(final Content content, final boolean select) {
    GridCell cell = getCellFor(content);
    cell.add(content);
    myContents.add(content);
    myContent2Cell.put(content, cell);
    Collections.sort(myContents, myContentComparator);
  }

  public void remove(final Content content) {
    getCellFor(content).remove(content);
    myContents.remove(content);
    myContent2Cell.remove(content);
  }

  public void setToolbarHorizontal(boolean horizontal) {
    for (final GridCell cell : myPlaceInGrid2Cell.values()) {
      cell.setToolbarHorizontal(horizontal);
    }
  }

  private GridCell getCellFor(final Content content) {
    final GridCell cell = myPlaceInGrid2Cell.get(getStateFor(content).getPlaceInGrid());
    assert cell != null : "Unknown place in grid: " + getStateFor(content).getPlaceInGrid().name();
    return cell;
  }

  private NewContentState getStateFor(final Content content) {
    return mySettings.getNewContentState(content);
  }

  public void updateGridUI(boolean restorePropertions) {
    Iterator<GridCell> cells = myPlaceInGrid2Cell.values().iterator();
    while (cells.hasNext()) {
      GridCell each = cells.next();
      each.setHideTabs(myContents.size() == 1);
    }

    if (restorePropertions) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          restoreProportions();
        }
      });
    }
  }

  public boolean isEmpty() {
    return myContent2Cell.isEmpty();
  }

  static class Placeholder extends Wrapper implements NullableComponent {
    public boolean isNull() {
      return getComponentCount() == 0;
    }
  }

  public void dispose() {
  }

  void restoreProportion(PlaceInGrid placeInGrid) {
    final float proportion = mySettings.getSplitProportion(placeInGrid);
    switch (placeInGrid) {
      case left:
        setLeftProportion(proportion);
        break;
      case right:
        setRightProportion(proportion);
        break;
      case bottom:
        mySplitter.setProportion(proportion);
      case center:
        break;
      case unknown:
        break;
    }
  }


  float getLeftProportion() {
    final float totalSize = myTopSplit.getOrientation() ? myTopSplit.getHeight() : myTopSplit.getWidth();
    final float componentSize = myTopSplit.getFirstSize();
    return componentSize / (totalSize - 2.0f * myTopSplit.getDividerWidth());
  }

  void setLeftProportion(float proportion) {
    final int totalSize = myTopSplit.getOrientation() ? myTopSplit.getHeight() : myTopSplit.getWidth();
    myTopSplit.setFirstSize((int)(proportion * (float)(totalSize - 2 * myTopSplit.getDividerWidth())));
  }

  float getRightProportion() {
    final float totalSize = myTopSplit.getOrientation() ? myTopSplit.getHeight() : myTopSplit.getWidth();
    final float componentSize = myTopSplit.getLastSize();
    return componentSize / (totalSize - 2.0f * myTopSplit.getDividerWidth());
  }

  float getBottomPropertion() {
    final float totalSize = mySplitter.getOrientation() ? mySplitter.getHeight() : mySplitter.getWidth();
    final float componentSize =
      mySplitter.getOrientation() ? mySplitter.getFirstComponent().getHeight() : mySplitter.getFirstComponent().getWidth();
    return componentSize / (totalSize - mySplitter.getDividerWidth());
  }

  void setRightProportion(float proportion) {
    final int componentSize = myTopSplit.getOrientation() ? myTopSplit.getHeight() : myTopSplit.getWidth();
    myTopSplit.setLastSize((int)(proportion * (float)(componentSize - 2 * myTopSplit.getDividerWidth())));
  }

  public List<Content> getContents() {
    return myContents;
  }

  public void minimize(final Content content, final CellTransform.Restore restore) {
    myUI.minimize(content, new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        // my restore
        return restore.restoreInGrid();
      }
    });
  }
}
