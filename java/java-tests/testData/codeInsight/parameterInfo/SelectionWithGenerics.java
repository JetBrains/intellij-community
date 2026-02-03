class Test {
  {
    refresh(<caret>false, false, null, "");
  }

  public final void refresh(boolean async, boolean recursive,  Runnable finishRunnable, String... files) {
  }

  public final void refresh(boolean async, boolean recursive, Runnable finishRunnable, Integer files) {
  }

}
