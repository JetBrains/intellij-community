package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.ResultViewColumn;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.util.List;

public abstract class TableResultViewColumn extends TableColumn implements ResultViewColumn {
  private final UserDataHolder myDataHolderDelegate = new UserDataHolderBase();
  private int myWidthFromLayout;

  public TableResultViewColumn(int modelIndex) {
    super(modelIndex);
  }

  public abstract @NlsContexts.Tooltip @Nullable String getTooltipText();

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    return myDataHolderDelegate.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDataHolderDelegate.putUserData(key, value);
  }

  @Override
  public int getColumnWidth() {
    return getPreferredWidth();
  }

  @Override
  public void setColumnWidth(int width) {
    myWidthFromLayout = width;
    setPreferredWidth(width);
  }

  public boolean isWidthSetByLayout() {
    return myWidthFromLayout == getPreferredWidth();
  }

  public abstract Icon getIcon(boolean display);

  @Override
  public abstract String getHeaderValue();

  public abstract List<String> getMultilineHeaderValues();
}
