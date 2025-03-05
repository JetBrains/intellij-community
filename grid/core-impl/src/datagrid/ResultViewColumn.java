package com.intellij.database.datagrid;

import com.intellij.openapi.util.UserDataHolder;

/**
* @author gregsh
*/
public interface ResultViewColumn extends UserDataHolder {
  int ADDITIONAL_COLUMN_WIDTH = 8;

  int getColumnWidth();

  void setColumnWidth(int width);

  int getModelIndex();

  String getHeaderValue();

  default int getAdditionalWidth() {
    return ADDITIONAL_COLUMN_WIDTH;
  }
}
