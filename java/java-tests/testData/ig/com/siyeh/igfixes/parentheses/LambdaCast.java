class Inversion {
  public Runnable context() {
    return (Runnable) (<caret>() -> {});
  }
}