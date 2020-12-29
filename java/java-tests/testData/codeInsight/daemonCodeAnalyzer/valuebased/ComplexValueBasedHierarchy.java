import valuebased.classes.OpenValueBased;
class One extends OpenValueBased { }
class Two extends One { }
class Three extends Two { }
class ComplexVBHierarchy extends Three { }

class Main {
  final ComplexVBHierarchy vb = new ComplexVBHierarchy();
  {
    final ComplexVBHierarchy localVb = new ComplexVBHierarchy();
    final Object objectVb = new ComplexVBHierarchy();

    synchronized (<warning descr="Attempt to synchronize on an instance of a value-based class">vb</warning>) {}
    synchronized (<warning descr="Attempt to synchronize on an instance of a value-based class">localVb</warning>) {}
    synchronized (<warning descr="Attempt to synchronize on an instance of a value-based class">objectVb</warning>) {}
    synchronized (ComplexVBHierarchy.class) {}
    f(vb);
    g(vb);
  }

  void f(ComplexVBHierarchy vb) {
    synchronized (<warning descr="Attempt to synchronize on an instance of a value-based class">vb</warning>) {}
  }

  void g(Object vb) {
    synchronized (vb) {}
  }

  @SuppressWarnings("synchronization")
  void h(ComplexVBHierarchy vb) {
    synchronized (vb) {}
  }
}
