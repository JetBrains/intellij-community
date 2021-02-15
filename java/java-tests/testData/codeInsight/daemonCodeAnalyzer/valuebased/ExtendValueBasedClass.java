import valuebased.classes.AbstractValueBased;

class AC extends AbstractValueBased {
  final AC ac = new AC();
  final Object objectAc = new AC();
  {
    final AC localAc = new AC();

    synchronized (<warning descr="Attempt to synchronize on an instance of a value-based class">ac</warning>) {}
    synchronized (<warning descr="Attempt to synchronize on an instance of a value-based class">objectAc</warning>) {}
    synchronized (<warning descr="Attempt to synchronize on an instance of a value-based class">localAc</warning>) {}
    synchronized (AC.class) {}

    synchronized (new Object()) {}

    f(ac);
    g(ac);
  }

  void f(AC ac) {
    synchronized (<warning descr="Attempt to synchronize on an instance of a value-based class">ac</warning>) {}
  }

  void g(Object ac) {
    synchronized (ac) {}
  }

  @SuppressWarnings("synchronization")
  void h(AC ac) {
    synchronized (ac) {}
  }
}
