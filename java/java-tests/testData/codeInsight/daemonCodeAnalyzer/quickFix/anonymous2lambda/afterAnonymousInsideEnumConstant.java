// "Replace with lambda" "true-preview"
enum E {
  A(() -> {});

  public E(Runnable r) {}
}
