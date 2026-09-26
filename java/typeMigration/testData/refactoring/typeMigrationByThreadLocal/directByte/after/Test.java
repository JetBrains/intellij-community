class Test {
  ThreadLocal<Byte> i;

  void foo() {
    i.set((byte) (i.get() + 1));
    i.set((byte) (i.get() + 1));
    i.set((byte) (i.get() - 1));
    i.set((byte) (i.get() - 1));
    if (i.get() == 0);
  }
}