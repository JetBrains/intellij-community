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

  default void clearWidthSetByUser() {
  }

  int getModelIndex();

  String getHeaderValue();

  default int getAdditionalWidth() {
    return ADDITIONAL_COLUMN_WIDTH;
  }

  default void dropModelDependentCache() {}
}
