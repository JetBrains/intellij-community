package com.intellij.ui.navigation;

public interface HistoryListener {

  void navigationStarted(Place from, Place to);
  void navigationFinished(Place from, Place to);

  class Adapter implements HistoryListener {
    public void navigationStarted(final Place from, final Place to) {
    }

    public void navigationFinished(final Place from, final Place to) {
    }
  }

}