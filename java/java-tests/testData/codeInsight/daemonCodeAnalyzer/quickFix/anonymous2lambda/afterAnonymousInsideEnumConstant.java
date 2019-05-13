// "Replace with lambda" "true"
enum E {
  A(() -> {});

  public E(Runnable r) {}
}
