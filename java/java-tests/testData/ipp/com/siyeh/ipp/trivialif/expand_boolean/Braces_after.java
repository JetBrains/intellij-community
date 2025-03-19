class Braces {
  void m() {
    final boolean isEqualsVoorSyncResync;//keep me
      if (this instanceof A ? isEqual2() : isEqual()) {
          isEqualsVoorSyncResync = true;
      } else {
          isEqualsVoorSyncResync = false;
      }
  }

  private boolean isEqual() {
    return false;
  }

  private boolean isEqual2() {
    return false;
  }
}