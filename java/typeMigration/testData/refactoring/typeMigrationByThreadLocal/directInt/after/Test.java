class Test {
  ThreadLocal<Integer> i;

  void foo() {
    i.set(i.get() + 1);
    i.set(i.get() + 1);
    i.set(i.get() - 1);
    i.set(i.get() - 1);
    if (i.get() == 0);
  }
}