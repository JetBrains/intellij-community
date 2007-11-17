package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class GridContentContainer extends Wrapper implements ContentContainer, Disposable {
  private DebuggerSettings mySettings;

  private ThreeComponentsSplitter myTopSplit = new ThreeComponentsSplitter();
  private Splitter mySplitter = new Splitter(true);

  private HashMap<PlaceInGrid, Cell> myPlaceInGrid2Cell = new HashMap<PlaceInGrid, Cell>();
  private ActionManager myActionManager;

  private Placeholder myLeft = new Placeholder();
  private Placeholder myCenter = new Placeholder();
  private Placeholder myRight = new Placeholder();
  private Placeholder myBottom = new Placeholder();

  private String mySessionName;

  public GridContentContainer(ActionManager actionManager, DebuggerSettings settings, Disposable parent, String sessionName, boolean horizontalToolbars) {
    Disposer.register(parent, this);

    mySettings = settings;
    myActionManager = actionManager;
    mySessionName = sessionName;

    setOpaque(false);

    myPlaceInGrid2Cell.put(PlaceInGrid.left, new Cell(myLeft, horizontalToolbars, PlaceInGrid.left));
    myPlaceInGrid2Cell.put(PlaceInGrid.center, new Cell(myCenter, horizontalToolbars, PlaceInGrid.center));
    myPlaceInGrid2Cell.put(PlaceInGrid.right, new Cell(myRight, horizontalToolbars, PlaceInGrid.right));
    myPlaceInGrid2Cell.put(PlaceInGrid.bottom, new Cell(myBottom, horizontalToolbars, PlaceInGrid.bottom));

    setContent(mySplitter);

    myTopSplit.setFirstComponent(myLeft);
    myTopSplit.setInnerComponent(myCenter);
    myTopSplit.setLastComponent(myRight);
    mySplitter.setFirstComponent(myTopSplit);
    mySplitter.setSecondComponent(myBottom);
  }

  public void addNotify() {
    super.addNotify();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        restoreProportions();
      }
    });
  }

  private void restoreProportions() {
    for (final Cell cell : myPlaceInGrid2Cell.values()) {
      cell.restoreProportion();
    }
  }

  public void add(final Content content, final boolean select) {
    getCellFor(content).add(content);
  }

  public void remove(final Content content) {
    getCellFor(content).remove(content);
  }

  public void setToolbarHorizontal(boolean horizontal) {
    for (final Cell cell : myPlaceInGrid2Cell.values()) {
      cell.setToolbarHorizontal(horizontal);
    }
  }

  private GridContentContainer.Cell getCellFor(final Content content) {
    final GridContentContainer.Cell cell = myPlaceInGrid2Cell.get(getStateFor(content).getPlaceInGrid());
    assert cell != null : "Unknown place in grid: " + getStateFor(content).getPlaceInGrid().name();
    return cell;
  }

  private NewContentState getStateFor(final Content content) {
    return mySettings.getNewContentState(content);
  }


  class Cell {

    private List<Content> myContents = new ArrayList<Content>();
    private JBTabs myTabs;
    private Placeholder myPlaceholder;
    private PlaceInGrid myPlaceInGrid;

    public Cell(Placeholder placeholder, boolean horizontalToolbars, PlaceInGrid placeInGrid) {
      myPlaceInGrid = placeInGrid;
      myPlaceholder = placeholder;
      myTabs = new JBTabs(myActionManager, GridContentContainer.this);
      myTabs.setSideComponentVertical(!horizontalToolbars);
    }

    void add(Content content) {
      if (myContents.contains(content)) return;
      myContents.add(content);

      revalidateCell();
    }

    void remove(Content content) {
      if (!myContents.contains(content)) return;
      myContents.remove(content);

      revalidateCell();
    }

    private void revalidateCell() {

      if (myContents.size() == 0) {
        myPlaceholder.removeAll();
      } else {
        if (myPlaceholder.isNull()) {
          myPlaceholder.setContent(myTabs);
        }

        myTabs.removeAllTabs();
        for (Content each : myContents) {
          myTabs.addTab(getTabInfoFor(each));
        }
      }

      restoreProportion();

      myTabs.revalidate();
      myTabs.repaint();
    }

    private TabInfo getTabInfoFor(Content content) {
      final JComponent c = content.getComponent();

      NewDebuggerContentUI.removeScrollBorder(c);

      return new TabInfo(c)
        .setIcon(content.getIcon())
        .setText(content.getDisplayName())
        .setActions(content.getActions(), content.getPlace())
        .setObject(content)
        .setPreferredFocusableComponent(content.getPreferredFocusableComponent());
    }

    public void setToolbarHorizontal(final boolean horizontal) {
      myTabs.setSideComponentVertical(!horizontal);
    }

    public void restoreProportion() {
      GridContentContainer.this.restoreProportion(myPlaceInGrid);
    }
  }


  private static class Placeholder extends Wrapper implements NullableComponent {
    public boolean isNull() {
      return getComponentCount() == 0;
    }
  }

  public void dispose() {
  }

  private void restoreProportion(PlaceInGrid placeInGrid) {
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

}
