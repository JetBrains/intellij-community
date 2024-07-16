// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.navigation;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public final class History {
  public static final DataKey<History> KEY = DataKey.create("History");

  private final List<Place> history = new ArrayList<>();
  private int currentPos;
  private final Place.Navigator root;

  private boolean navigatedNow;
  private final CopyOnWriteArraySet<HistoryListener> listeners = new CopyOnWriteArraySet<>();

  public History(@NotNull Place.Navigator root) {
    this.root = root;
  }

  @RequiresEdt
  public void pushQueryPlace() {
    if (isNavigatingNow()) return;

    final Place place = query();
    pushPlace(place);
  }

  private synchronized void pushPlace(@NotNull Place place) {
    while (currentPos >= 0 && currentPos < history.size() - 1) {
      history.remove(history.size() - 1);
    }

    if (!history.isEmpty()) {
      final Place prev = history.get(history.size() - 1);
      if (prev.equals(place)) return;

      if (prev.isMoreGeneralFor(place)) {
        history.remove(prev);
      }
    }

    addPlace(place);
  }

  private synchronized void addPlace(Place place) {
    history.add(place);
    currentPos = history.size() - 1;
  }

  public void pushPlaceForElement(String name, Object value) {
    if (!canNavigateFor(name)) return;

    final Place checkPlace = getCheckPlace(name);
    if (checkPlace == null) return;
    pushPlace(checkPlace.cloneForElement(name, value));
  }

  public void navigateTo(Place place) {
    root.navigateTo(place, false);
  }

  @RequiresEdt
  public void back() {
    int next = findValid(-1);
    if (next == -1) return;
    goThere(next);
  }

  private int findValid(int increment) {
    List<Place> places = new ArrayList<>();
    int first;
    synchronized (this) {
      first = currentPos + increment;
      for (int idx = first; idx >= 0 && idx < history.size(); idx += increment) {
        places.add(history.get(idx));
      }
    }
    int index = ContainerUtil.indexOf(places, place -> root.isValid(place));
    return index == -1 ? -1 : first + index * increment;
  }

  private void goThere(final int nextPos) {
    navigatedNow = true;
    final Place next = history.get(nextPos);
    final Place from = getCurrent();
    fireStarted(from, next);
    try {
      final ActionCallback callback = root.navigateTo(next, false);
      assert callback != null;
      callback.doWhenDone(() -> {
        synchronized (this) {
          currentPos = nextPos;
        }
      }).doWhenProcessed(() -> {
        navigatedNow = false;
        fireFinished(from, next);
      });
    }
    catch (Throwable e) {
      navigatedNow = false;
      throw new RuntimeException(e);
    }
  }

  public boolean isNavigatingNow() {
    return navigatedNow;
  }

  public boolean canGoBack() {
    return findValid(-1) != -1;
  }

  @RequiresEdt
  public void forward() {
    int next = findValid(1);
    if (next == -1) return;
    goThere(next);
  }

  public boolean canGoForward() {
    return findValid(1) != -1;
  }

  @RequiresEdt
  public synchronized void clear() {
    history.clear();
    currentPos = -1;
  }

  public @NotNull Place query() {
    Place result = new Place();
    root.queryPlace(result);
    return result;
  }

  private synchronized Place getCurrent() {
    if (currentPos >= 0 && currentPos < history.size()) {
      return history.get(currentPos);
    } else {
      return null;
    }
  }

  private boolean canNavigateFor(String pathElement) {
    if (isNavigatingNow()) return false;

    Place checkPlace = getCheckPlace(pathElement);

    return checkPlace != null && checkPlace.getPath(pathElement) != null;
  }

  private @Nullable Place getCheckPlace(String pathElement) {
    Place checkPlace = getCurrent();
    if (checkPlace == null || checkPlace.getPath(pathElement) == null) {
      checkPlace = query();
    }

    return checkPlace.getPath(pathElement) != null ? checkPlace : null;
  }

  public void addListener(final HistoryListener listener, Disposable parent) {
    listeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        listeners.remove(listener);
      }
    });
  }

  private void fireStarted(Place from, Place to) {
    for (HistoryListener each : listeners) {
      each.navigationStarted(from, to);
    }
  }

  private void fireFinished(Place from, Place to) {
    for (HistoryListener each : listeners) {
      each.navigationFinished(from, to);
    }
  }
}
