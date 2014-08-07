class Test {
  ThreadLocal i;

  void foo() {
    i.set(new Integer(((Integer) i.get()).intValue() + 1));
    i.set(new Integer(((Integer) i.get()).intValue() + 1));
    i.set(new Integer(((Integer) i.get()).intValue() - 1));
    i.set(new Integer(((Integer) i.get()).intValue() - 1));
    if (((Integer) i.get()).intValue() == 0);
  }
}