package com.intellij.grid.core.impl.icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class GridCoreImplIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, GridCoreImplIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, GridCoreImplIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon CellDownload = load("icons/cellDownload.svg", 18297100, 2);
  /** 16x16 */ public static final @NotNull Icon ClearOutputs = load("icons/expui/clearOutputs.svg", -737519268, 2);
  /** 16x16 */ public static final @NotNull Icon ColumnFilter = load("icons/expui/columnFilter.svg", "icons/columnFilter.svg", -1426207601, 0);
  /** 16x16 */ public static final @NotNull Icon FilterHistory = load("icons/expui/filterHistory.svg", "icons/filterHistory.svg", 686246081, 0);
  /** 16x16 */ public static final @NotNull Icon SingleRecordView = load("icons/expui/singleRecordView.svg", "icons/singleRecordView.svg", 502511093, 2);
  /** 16x16 */ public static final @NotNull Icon SortHistory = load("icons/expui/sortHistory.svg", "icons/sortHistory.svg", 1373397606, 0);
  /** 16x16 */ public static final @NotNull Icon StatisticsPanel = load("icons/expui/statisticsPanel.svg", "icons/statisticsPanel.svg", -153769984, 0);
  /** 16x16 */ public static final @NotNull Icon TableHeatmap = load("icons/expui/tableHeatmap.svg", "icons/tableHeatmap.svg", -992154187, 0);
}
