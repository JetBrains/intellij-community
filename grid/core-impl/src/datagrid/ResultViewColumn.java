package com.intellij.database.datagrid;

import com.intellij.openapi.util.UserDataHolder;

/**
* @author gregsh
*/
public interface ResultViewColumn extends UserDataHolder {
  int ADDITIONAL_COLUMN_WIDTH = 8;

  int getColumnWidth();

  void setColumnWidth(int width);

  /** Sets a width explicitly chosen by the user (persisted, and protected from auto-layout). */
  default void setColumnWidthByUser(int width) {
    setColumnWidth(width);
  }

  default boolean isWidthSetByUser() {
    return false;
  }

  /** Whether auto-layout must leave this width unchanged. Usually equivalent to {@link #isWidthSetByUser()}. */
  default boolean isWidthLockedForLayout() {
    return isWidthSetByUser();
  }

  default void clearWidthSetByUser() {
  }

  /** Hidden (width 0) in this view because it is shown in the frozen pinned region instead. Skipped by layout. */
  default boolean isFrozenHidden() {
    return false;
  }

  default void setFrozenHidden(boolean hidden) {
  }

  int getModelIndex();

  String getHeaderValue();

  default int getAdditionalWidth() {
    return ADDITIONAL_COLUMN_WIDTH;
  }

  default void dropModelDependentCache() {}
}
