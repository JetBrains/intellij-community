package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.ResultViewColumn;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.table.TableColumn;
import java.util.List;

public abstract class TableResultViewColumn extends TableColumn implements ResultViewColumn {
  private final UserDataHolder myDataHolderDelegate = new UserDataHolderBase();
  private int myWidthFromLayout;
  private boolean myWidthSetByUser;
  private boolean myWidthLockedForLayout;
  private @Nullable ColumnPins myPins;

  public TableResultViewColumn(int modelIndex) {
    super(modelIndex);
  }

  void setPins(@Nullable ColumnPins pins) {
    myPins = pins;
  }

  /** The column keeps no pin state of its own: it asks the view's columns, and a column of no view is never pinned. */
  boolean isPinned() {
    return myPins != null && myPins.isPinned(this);
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
    myWidthSetByUser = false;
    myWidthLockedForLayout = false;
    setPreferredWidth(width);
  }

  @Override
  public void setColumnWidthByUser(int width) {
    myWidthSetByUser = true;
    myWidthLockedForLayout = true;
    setPreferredWidth(width);
    // No table lays a pinned column out, so it takes the width at once.
    if (isPinned()) setWidth(width);
  }

  /** Width this column takes in the pinned strip: a user width wins, otherwise the wider of laid-out and preferred. */
  int getFrozenStripWidth() {
    return myWidthSetByUser ? getColumnWidth() : Math.max(getWidth(), getColumnWidth());
  }

  void setFrozenColumnWidth(int width, boolean widthSetByUser) {
    myWidthSetByUser = widthSetByUser;
    myWidthLockedForLayout = true;
    setPreferredWidth(width);
  }

  @Override
  public boolean isWidthSetByUser() {
    return myWidthSetByUser;
  }

  @Override
  public boolean isWidthLockedForLayout() {
    return myWidthLockedForLayout;
  }

  @Override
  public void clearWidthSetByUser() {
    myWidthSetByUser = false;
    myWidthLockedForLayout = false;
  }

  public boolean isWidthSetByLayout() {
    return myWidthFromLayout == getPreferredWidth();
  }

  public abstract Icon getIcon(boolean display);

  @Override
  public abstract String getHeaderValue();

  public abstract List<String> getMultilineHeaderValues();
}
