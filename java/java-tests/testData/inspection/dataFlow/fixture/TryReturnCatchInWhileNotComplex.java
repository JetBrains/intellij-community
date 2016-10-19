class Bar {
  void repeatUntilPassesInSmartMode(final Runnable r) {
    while (true) {
      try {
        r.run();
        return;
      }
      catch (Throwable e) {
      }
    }
  }
}
