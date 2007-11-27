package com.intellij.debugger.ui.content.newUI;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.util.containers.HashMap;

import java.util.*;

class Grid extends Wrapper implements Disposable, CellTransform.Facade {

  private ThreeComponentsSplitter myTopSplit = new ThreeComponentsSplitter();
  private Splitter mySplitter = new Splitter(true);

  private HashMap<PlaceInGrid, GridCell> myPlaceInGrid2Cell = new HashMap<PlaceInGrid, GridCell>();

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

  private boolean myLastUiStateWasRestored;
  private ViewContext myViewContext;

  public Grid(ViewContext viewContext, String sessionName) {
    myViewContext = viewContext;
    mySessionName = sessionName;

    Disposer.register(myViewContext, this);

    myPlaceInGrid2Cell.put(PlaceInGrid.left, new GridCell(myViewContext, this, myLeft, PlaceInGrid.left));
    myPlaceInGrid2Cell.put(PlaceInGrid.center, new GridCell(myViewContext, this, myCenter, PlaceInGrid.center));
    myPlaceInGrid2Cell.put(PlaceInGrid.right, new GridCell(myViewContext, this, myRight, PlaceInGrid.right));
    myPlaceInGrid2Cell.put(PlaceInGrid.bottom, new GridCell(myViewContext, this, myBottom, PlaceInGrid.bottom));

    setContent(mySplitter);
    setOpaque(false);
    setFocusCycleRoot(true);


    myTopSplit.setFirstComponent(myLeft);
    myTopSplit.setInnerComponent(myCenter);
    myTopSplit.setLastComponent(myRight);
    mySplitter.setFirstComponent(myTopSplit);
    mySplitter.setSecondComponent(myBottom);

  }

  public void addNotify() {
    super.addNotify();

    if (!myLastUiStateWasRestored) {
      myLastUiStateWasRestored = true;
      restoreLastUiState();
    }

    updateSelection(true);
  }

  public void removeNotify() {
    super.removeNotify();

    if (Disposer.isDisposed(this)) return;

    updateSelection(false);
  }

  private void updateSelection(boolean isShowing) {
    for (GridCell each: myPlaceInGrid2Cell.values()) {
      each.updateSelection(isShowing);     
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

  NewContentState getStateFor(final Content content) {
    return myViewContext.getStateFor(content);
  }

  public void updateGridUI() {
    for (final GridCell cell : myPlaceInGrid2Cell.values()) {
      cell.setHideTabs(myContents.size() == 1);
    }
  }

  public boolean isEmpty() {
    return myContent2Cell.isEmpty();
  }

  public void restoreLastUiState() {
    for (final GridCell cell : myPlaceInGrid2Cell.values()) {
      cell.restoreLastUiState();
    }
  }

  public void saveUiState() {
    for (final GridCell cell : myPlaceInGrid2Cell.values()) {
      cell.saveUiState();
    }
  }

  public Tab getTabIndex() {
    return getTab();
  }

  static class Placeholder extends Wrapper implements NullableComponent {
    public boolean isNull() {
      return getComponentCount() == 0;
    }
  }

  public void dispose() {

  }

  void saveSplitterProportions(final PlaceInGrid placeInGrid) {
    if (!NewDebuggerContentUI.ensureValid(this)) return;

    switch (placeInGrid) {
      case left:
        getTab().setLeftProportion(getLeftProportion());
        break;
      case right:
        getTab().setRightProportion(getRightProportion());
        break;
      case bottom:
        getTab().setBottomProportion(getBottomPropertion());
      case center:
        break;
    }
  }

  private Tab getTab() {
    return myViewContext.getTabFor(this);
  }

  void restoreLastSplitterProportions(PlaceInGrid placeInGrid) {
    if (!NewDebuggerContentUI.ensureValid(this)) return;

    switch (placeInGrid) {
      case left:
        setLeftProportion(getTab().getLeftProportion());
        break;
      case right:
        setRightProportion(getTab().getRightProportion());
        break;
      case bottom:
        mySplitter.setProportion(getTab().getBottomProportion());
      case center:
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
    myViewContext.getCellTransform().minimize(content, new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        return restore.restoreInGrid();
      }
    });
  }
}
