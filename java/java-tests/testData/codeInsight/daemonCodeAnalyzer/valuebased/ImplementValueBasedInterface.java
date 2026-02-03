import valuebased.classes.IValueBased;

class IVB implements IValueBased {
  final IVB vb = new IVB();
  {
    final IVB localVb = new IVB();
    final Object objectVb = new IVB();

    synchronized (<warning descr="Synchronization on instance of value-based class">vb</warning>) {}
    synchronized (<warning descr="Synchronization on instance of value-based class">localVb</warning>) {}
    synchronized (<warning descr="Synchronization on instance of value-based class">objectVb</warning>) {}
    synchronized (IValueBased.class) {}
    synchronized (IVB.class) {}
    f(vb);
    g(vb);
  }

  void f(IVB vb) {
    synchronized (<warning descr="Synchronization on instance of value-based class">vb</warning>) {}
  }

  void g(Object vb) {
    synchronized (vb) {}
  }

  @SuppressWarnings("synchronization")
  void h(IVB vb) {
    synchronized (vb) {}
  }
}
