package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.*;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class GridSelectionGrower {
  private final DataGrid myPanel;
  private final ResultView myResultView;
  private final JBTable myTable;

  private boolean myAdjusting;
  private GridSelection<GridRow, GridColumn> myPrevious;
  private final List<GridSelection<GridRow, GridColumn>> myGrowthHistory = new ArrayList<>();

  GridSelectionGrower(@NotNull DataGrid panel, @NotNull ResultView resultView, @NotNull JBTable table) {
    myPanel = panel;
    myResultView = resultView;
    myTable = table;

    myResultView.addSelectionChangedListener(isAdjusting -> {
      if (!myAdjusting && !isAdjusting) myGrowthHistory.clear();
    });
  }

  void growSelection() {
    if (myAdjusting) return;
    try {
      myAdjusting = true;
      growSelectionInternal();
    }
    finally {
      myAdjusting = false;
    }
  }

  void shrinkSelection() {
    if (myAdjusting) return;
    try {
      myAdjusting = true;
      shrinkSelectionInternal();
    }
    finally {
      myAdjusting = false;
    }
  }

  void reset() {
    if (myAdjusting) return;
    myPrevious = null;
  }

  private void growSelectionInternal() {
    GridSelectionGrowerStrategy grower = GridSelectionGrowerStrategy.of(myPanel, myResultView);
    if (grower == null) return;
    myGrowthHistory.add(myPanel.getSelectionModel().store());
    if (grower.isNeedRestore() && myPrevious != null) restore();
    myPrevious = grower.isNeedStore() ? myPanel.getSelectionModel().store() : myPrevious;
    grower.changeSelection(myPanel, myResultView, myTable);
  }

  private void shrinkSelectionInternal() {
    if (myGrowthHistory.isEmpty()) return;
    GridSelection<GridRow, GridColumn> previous = myGrowthHistory.get(myGrowthHistory.size() - 1);
    myPanel.getAutoscrollLocker().runWithLock(() -> myPanel.getSelectionModel().restore(previous));
    myGrowthHistory.remove(myGrowthHistory.size() - 1);
  }

  private void restore() {
    myPanel.getAutoscrollLocker().runWithLock(() -> myPanel.getSelectionModel().restore(myPrevious));
  }
}
