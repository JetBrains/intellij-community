package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class GridImpl extends Wrapper implements Grid, Disposable, CellTransform.Facade, DataProvider {

  private ThreeComponentsSplitter myTopSplit = new ThreeComponentsSplitter();
  private Splitter mySplitter = new Splitter(true);

  private HashMap<PlaceInGrid, GridCellImpl> myPlaceInGrid2Cell = new HashMap<PlaceInGrid, GridCellImpl>();

  private Placeholder myLeft = new Placeholder();
  private Placeholder myCenter = new Placeholder();
  private Placeholder myRight = new Placeholder();
  private Placeholder myBottom = new Placeholder();

  private String mySessionName;

  private List<Content> myContents = new ArrayList<Content>();
  private Map<Content, GridCellImpl> myContent2Cell = new java.util.HashMap<Content, GridCellImpl>();

  private Comparator<Content> myContentComparator = new Comparator<Content>() {
    public int compare(final Content o1, final Content o2) {
      return getCellFor(o1).getPlaceInGrid().compareTo(getCellFor(o2).getPlaceInGrid());
    }
  };

  private boolean myLastUiStateWasRestored;
  private ViewContext myViewContext;

  public GridImpl(ViewContext viewContext, String sessionName) {
    myViewContext = viewContext;
    mySessionName = sessionName;

    Disposer.register(myViewContext, this);

    myPlaceInGrid2Cell.put(PlaceInGrid.left, new GridCellImpl(myViewContext, this, myLeft, PlaceInGrid.left));
    myPlaceInGrid2Cell.put(PlaceInGrid.center, new GridCellImpl(myViewContext, this, myCenter, PlaceInGrid.center));
    myPlaceInGrid2Cell.put(PlaceInGrid.right, new GridCellImpl(myViewContext, this, myRight, PlaceInGrid.right));
    myPlaceInGrid2Cell.put(PlaceInGrid.bottom, new GridCellImpl(myViewContext, this, myBottom, PlaceInGrid.bottom));

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


    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      cell.restoreProportions();
    }

    updateSelection(true);
  }

  public void removeNotify() {
    super.removeNotify();

    if (Disposer.isDisposed(this)) return;

    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      cell.saveProportions();
    }

    updateSelection(false);
  }

  private void updateSelection(boolean isShowing) {
    for (GridCellImpl each: myPlaceInGrid2Cell.values()) {
      each.updateSelection(isShowing);
    }
  }


  public void add(final Content content, final boolean select) {
    GridCellImpl cell = getCellFor(content);
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
    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      cell.setToolbarHorizontal(horizontal);
    }
  }

  public GridCellImpl getCellFor(final Content content) {
    final GridCellImpl cell = myPlaceInGrid2Cell.get(getStateFor(content).getPlaceInGrid());
    assert cell != null : "Unknown place in grid: " + getStateFor(content).getPlaceInGrid().name();
    return cell;
  }

  View getStateFor(final Content content) {
    return myViewContext.getStateFor(content);
  }

  public boolean updateGridUI() {
    boolean hasToolbarContent = true;
    boolean wasHidden = false;
    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      final boolean eachToHide = myContents.size() == 1 && !cell.isDetached();
      cell.setHideTabs(eachToHide);
      wasHidden |= eachToHide;
    }

    if (wasHidden) {
      final Content onlyContent = myContents.get(0);
      hasToolbarContent = onlyContent.getSearchComponent() != null;      
    }


    return hasToolbarContent;
  }

  public boolean isEmpty() {
    return myContent2Cell.isEmpty();
  }

  public ActionCallback restoreLastUiState() {
    myLastUiStateWasRestored = true;
    final ActionCallback result = new ActionCallback(myPlaceInGrid2Cell.values().size());
    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      cell.restoreLastUiState().notifyWhenDone(result);
    }

    return result;
  }

  public void saveUiState() {
    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      cell.saveUiState();
    }
  }

  public Tab getTabIndex() {
    return getTab();
  }

  public ActionCallback select(final Content content, final boolean requestFocus) {
    return getCellFor(content).select(content, requestFocus);
  }

  public void alert(final Content content) {
    GridCellImpl cell = getCellFor(content);
    cell.alert(content);
  }

  @Nullable
  public GridCellImpl findCell(final Content content) {
    return myContent2Cell.get(content);
  }

  static class Placeholder extends Wrapper implements NullableComponent {

    private JComponent myContent;

    public boolean isNull() {
      return getComponentCount() == 0;
    }

    public CellTransform.Restore detach() {
      if (getComponentCount() == 1) {
        myContent = (JComponent)getComponent(0);
        removeAll();
      }

      if (getParent() instanceof JComponent) {
        ((JComponent)getParent()).revalidate();
        getParent().repaint();
      }

      return new CellTransform.Restore() {
        public ActionCallback restoreInGrid() {
          if (myContent != null) {
            setContent(myContent);
            myContent = null;
          }
          return new ActionCallback.Done();
        }
      };
    }
  }

  public void dispose() {

  }

  void saveSplitterProportions(final PlaceInGrid placeInGrid) {
    if (!RunnerContentUi.ensureValid(this)) return;

    final TabImpl tab = (TabImpl)getTab();

    switch (placeInGrid) {
      case left:
        tab.setLeftProportion(getLeftProportion());
        break;
      case right:
        tab.setRightProportion(getRightProportion());
        break;
      case bottom:
        tab.setBottomProportion(getBottomPropertion());
      case center:
        break;
    }
  }

  public Tab getTab() {
    return myViewContext.getTabFor(this);
  }

  void restoreLastSplitterProportions(PlaceInGrid placeInGrid) {
    if (!RunnerContentUi.ensureValid(this)) return;

    final TabImpl tab = (TabImpl)getTab();
    switch (placeInGrid) {
      case left:
        setLeftProportion(tab.getLeftProportion());
        break;
      case right:
        setRightProportion(tab.getRightProportion());
        break;
      case bottom:
        mySplitter.setProportion(tab.getBottomProportion());
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

  public List<Content> getAttachedContents() {
    ArrayList<Content> result = new ArrayList<Content>();

    for (Content each : getContents()) {
      if (!isDetached(each)) {
        result.add(each);
      }
    }

    return result;
  }


  private boolean isDetached(Content content) {
    return getCellFor(content).isDetached();
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

  public void moveToTab(final Content content) {
    myViewContext.getCellTransform().moveToTab(content);
  }

  public void moveToGrid(final Content content) {
    myViewContext.getCellTransform().moveToGrid(content);
  }

  public CellTransform.Restore detach(final Content[] content) {
    final CellTransform.Restore.List restore = new CellTransform.Restore.List();
    restore.add(myViewContext.getCellTransform().detach(content));
    restore.add(new CellTransform.Restore() {
      public ActionCallback restoreInGrid() {
        revalidate();
        repaint();
        return new ActionCallback.Done();
      }
    });

    return restore;
  }

  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (ViewContext.CONTEXT_KEY.getName().equals(dataId)) {
      return myViewContext;
    } else if (ViewContext.CONTENT_KEY.getName().equals(dataId)) {
      List<Content> contents = getContents();
      return contents.toArray(new Content[contents.size()]);
    }
    return null;
  }

  public String getSessionName() {
    return mySessionName;
  }
}
