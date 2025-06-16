// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

@ApiStatus.Internal
public final class GridImpl extends Wrapper implements Grid, Disposable, UiDataProvider {
  private final ThreeComponentsSplitter myTopSplit = new ThreeComponentsSplitter(false, true);
  private final Splitter mySplitter = new Splitter(true);

  private final Map<PlaceInGrid, GridCellImpl> myPlaceInGrid2Cell = new EnumMap<>(PlaceInGrid.class);

  private final List<Content> myContents = new ArrayList<>();
  private final Map<Content, GridCellImpl> myContent2Cell = new HashMap<>();

  private final Comparator<Content> myContentComparator = Comparator.comparing(o -> getCellFor(o).getPlaceInGrid());

  private final ViewContextEx myViewContext;

  public GridImpl(@NotNull ViewContextEx viewContext, String sessionName) {
    myViewContext = viewContext;

    Disposer.register(myViewContext, this);

    Placeholder left = new Placeholder();
    myPlaceInGrid2Cell.put(PlaceInGrid.left, new GridCellImpl(myViewContext, this, left, PlaceInGrid.left));
    Placeholder center = new Placeholder();
    myPlaceInGrid2Cell.put(PlaceInGrid.center, new GridCellImpl(myViewContext, this, center, PlaceInGrid.center));
    Placeholder right = new Placeholder();
    myPlaceInGrid2Cell.put(PlaceInGrid.right, new GridCellImpl(myViewContext, this, right, PlaceInGrid.right));
    Placeholder bottom = new Placeholder();
    myPlaceInGrid2Cell.put(PlaceInGrid.bottom, new GridCellImpl(myViewContext, this, bottom, PlaceInGrid.bottom));

    setContent(mySplitter);
    setOpaque(false);
    setFocusCycleRoot(!ScreenReader.isActive());


    myTopSplit.setFirstComponent(left);
    myTopSplit.setInnerComponent(center);
    myTopSplit.setLastComponent(right);
    myTopSplit.setMinSize(48);
    mySplitter.setFirstComponent(myTopSplit);
    mySplitter.setSecondComponent(bottom);
  }

  @Override
  public void addNotify() {
    super.addNotify();

    if (UIUtil.getParentOfType(JBRunnerTabs.class, this) != null) {
      processAddToUi(true);
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    processRemoveFromUi();
  }

  public void processAddToUi(boolean restoreProportions) {
    if (restoreProportions) {
      for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
        cell.restoreProportions();
      }
    }

    updateSelection(true);
  }


  public void processRemoveFromUi() {
    if (Disposer.isDisposed(this)) return;

    updateSelection(false);
  }

  private void updateSelection(boolean isShowing) {
    for (GridCellImpl each : myPlaceInGrid2Cell.values()) {
      each.updateSelection(isShowing);
    }
  }


  void add(final Content content) {
    GridCellImpl cell = getCellFor(content);
    cell.add(content);
    myContents.add(content);
    myContent2Cell.put(content, cell);
    myContents.sort(myContentComparator);
  }

  void remove(final Content content) {
    getCellFor(content).remove(content);
    myContents.remove(content);
    myContent2Cell.remove(content);
  }

  public void setToolbarHorizontal(boolean horizontal) {
    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      cell.setToolbarHorizontal(horizontal);
    }
  }

  public void setToolbarBefore(boolean before) {
    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      cell.setToolbarBefore(before);
    }
  }

  @Override
  public GridCellImpl getCellFor(final Content content) {
    // check if the content is already in some cell
    GridCellImpl current = myContent2Cell.get(content);
    if (current != null) return current;
    // view may be shared between several contents with the same ID in different cells
    // (temporary contents like "Dump Stack" or "Console Result")
    View view = getStateFor(content);
    final GridCellImpl cell = myPlaceInGrid2Cell.get(view.getPlaceInGrid());
    assert cell != null : "Unknown place in grid: " + view.getPlaceInGrid().name();
    return cell;
  }

  View getStateFor(final Content content) {
    return myViewContext.getStateFor(content);
  }

  public boolean updateGridUI() {
    var isHidden = myViewContext.getLayoutSettings().isTabLabelsHidden();
    for (final GridCellImpl cell : myPlaceInGrid2Cell.values()) {
      if (isHidden) {
        cell.setHideTabs(cell.getContentCount() == 1);
      } else {
        cell.setHideTabs(myContents.size() == 1);
      }
    }

    final Content onlyContent = myContents.get(0);

    return onlyContent.getSearchComponent() != null;
  }

  public boolean isEmpty() {
    return myContent2Cell.isEmpty();
  }

  public ActionCallback restoreLastUiState() {
    final ActionCallback result = new ActionCallback(myPlaceInGrid2Cell.size());
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

  public @Nullable Tab getTabIndex() {
    return getTab();
  }

  public ActionCallback select(final Content content, final boolean requestFocus) {
    return getCellFor(content).select(content, requestFocus);
  }

  public void processAlert(final Content content, final boolean activate) {
    GridCellImpl cell = getCellFor(content);
    cell.processAlert(content, activate);
  }

  public @Nullable GridCellImpl findCell(final Content content) {
    return myContent2Cell.get(content);
  }

  public void rebuildTabPopup() {
    final List<Content> contents = getContents();
    for (Content each : contents) {
      GridCellImpl cell = findCell(each);
      if (cell != null) {
        cell.rebuildPopupGroup();
      }
    }
  }

  public boolean isMinimized(Content content) {
    return getCellFor(content).isMinimized(content);
  }

  public interface ContentProvider {
    Content[] getContents();
  }

  @ApiStatus.Internal
  public static final class Placeholder extends Wrapper implements NullableComponent {
    private ContentProvider myContentProvider;
    private JComponent myComponent;

    {
      setFocusTraversalPolicyProvider(true);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
        @Override
        public Component getDefaultComponent(Container aContainer) {
          Component content = getContent(true);
          if (content != null) {
            return content;
          }
          return super.getDefaultComponent(aContainer);
        }

        @Override
        public Component getLastComponent(Container aContainer) {
          Component content = getContent(false);
          if (content != null) {
            return content;
          }
          return super.getLastComponent(aContainer);
        }

        private Component getContent(boolean first) {
          if (myContentProvider != null) {
            Content[] contents = myContentProvider.getContents();
            if (contents != null && contents.length > 0) {
              Component preferred = contents[first ? 0 : contents.length - 1].getPreferredFocusableComponent();
              if (preferred != null && preferred.isShowing() && accept(preferred)) {
                return preferred;
              }
            }
          }
          return null;
        }
      });
    }

    @VisibleForTesting
    public void setContentProvider(@NotNull ContentProvider provider) {
      myContentProvider = provider;
    }

    public CellTransform.Restore detach() {
      if (getComponentCount() == 1) {
        myComponent = (JComponent)getComponent(0);
        removeAll();
      }

      if (getParent() instanceof JComponent) {
        getParent().revalidate();
        getParent().repaint();
      }

      return new CellTransform.Restore() {
        @Override
        public ActionCallback restoreInGrid() {
          if (myComponent != null) {
            setContent(myComponent);
            myComponent = null;
          }
          return ActionCallback.DONE;
        }
      };
    }
  }

  @Override
  public void dispose() {

  }

  void saveSplitterProportions(final PlaceInGrid placeInGrid) {
    if (getRootPane() == null) return;
    final Rectangle bounds = getBounds();
    if (bounds.width == 0 && bounds.height == 0) return;

    final GridCellImpl cell = myPlaceInGrid2Cell.get(placeInGrid);

    if (!cell.isValidForCalculateProportions()) return;

    final TabImpl tab = (TabImpl)getTab();

    if (tab != null) {
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
  }

  public @Nullable Tab getTab() {
    return myViewContext.getTabFor(this);
  }

  void restoreLastSplitterProportions(PlaceInGrid placeInGrid) {
    if (getRootPane() == null) return;
    if (!RunnerContentUi.ensureValid(this)) return;

    final TabImpl tab = (TabImpl)getTab();
    if (tab != null) {
      switch (placeInGrid) {
        case left -> setLeftProportion(tab.getLeftProportion());
        case right -> setRightProportion(tab.getRightProportion());
        case bottom -> mySplitter.setProportion(tab.getBottomProportion());
        case center -> {}
      }
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

  public @NotNull ViewContextEx getViewContext() {
    return myViewContext;
  }

  @Override
  public @NotNull List<Content> getContents() {
    return myContents;
  }

  public void minimize(final Content content, final CellTransform.Restore restore) {
    myViewContext.getCellTransform().minimize(content, new CellTransform.Restore() {
      @Override
      public ActionCallback restoreInGrid() {
        return restore.restoreInGrid();
      }
    });
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(ViewContext.CONTEXT_KEY, myViewContext);
    sink.set(ViewContext.CONTENT_KEY, myContents.toArray(new Content[0]));
  }
}
