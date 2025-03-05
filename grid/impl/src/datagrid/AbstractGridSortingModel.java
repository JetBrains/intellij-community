package com.intellij.database.datagrid;

import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractGridSortingModel<Row, Column> implements GridSortingModel<Row, Column> {

  private final List<String> myHistory;
  private final Supplier<DataGridSettings> mySettingsSupplier;
  private boolean mySortingEnabled;
  private List<RowSortOrder<ModelIndex<Column>>> myAppliedOrdering = ContainerUtil.emptyList();
  protected List<RowSortOrder<ModelIndex<Column>>> myOrdering = ContainerUtil.emptyList();

  private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

  public AbstractGridSortingModel(@NotNull Supplier<DataGridSettings> settingsSupplier) {
    mySettingsSupplier = settingsSupplier;
    myHistory = new ArrayList<>();
  }

  public void setAppliedOrdering(@NotNull List<RowSortOrder<ModelIndex<Column>>> appliedOrdering) {
    myAppliedOrdering = appliedOrdering;
  }

  public @NotNull EventDispatcher<Listener> getEventDispatcher() {
    return myEventDispatcher;
  }

  @Override
  public boolean isSortingEnabled() {
    return mySortingEnabled;
  }

  @Override
  public void setSortingEnabled(boolean enabled) {
    mySortingEnabled = enabled;
    myEventDispatcher.getMulticaster().orderingChanged();
  }

  @Override
  public List<RowSortOrder<ModelIndex<Column>>> getOrdering() {
    return myOrdering;
  }

  @Override
  public void setOrdering(@NotNull List<RowSortOrder<ModelIndex<Column>>> ordering) {
    myOrdering = new ArrayList<>(ordering);
  }

  @Override
  public @NotNull List<RowSortOrder<ModelIndex<Column>>> getAppliedOrdering() {
    return isSortingEnabled() ? new ArrayList<>(myAppliedOrdering) : new SmartList<>();
  }

  @Override
  public void apply() {
    myAppliedOrdering = new ArrayList<>(myOrdering);
    myEventDispatcher.getMulticaster().orderingChanged();
  }

  protected void addToHistory(@NotNull String text) {
    myHistory.remove(text);
    myHistory.add(0, text);
    trimHistory();
  }

  @Override
  public void setHistory(@NotNull List<String> history) {
    myHistory.clear();
    myHistory.addAll(history);
  }

  private void trimHistory() {
    DataGridSettings settings = mySettingsSupplier.get();
    int historySize = Math.max(0, settings == null ? 10 : settings.getFiltersHistorySize());
    while (myHistory.size() > historySize) {
      myHistory.remove(myHistory.size() - 1);
    }
  }

  @Override
  public @Nullable Document getDocument() {
    return null;
  }

  @Override
  public @NotNull List<String> getHistory() {
    return myHistory;
  }

  @Override
  public void addListener(@NotNull Listener l, @NotNull Disposable disposable) {
    myEventDispatcher.addListener(l, disposable);
  }

  public void firePrefixUpdated() {
    myEventDispatcher.getMulticaster().onPrefixUpdated();
  }

  public void firePSIUpdated() {
    myEventDispatcher.getMulticaster().onPsiUpdated();
  }
}
