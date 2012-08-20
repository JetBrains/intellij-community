interface I1 {
  void m();
}

interface I2<X> {
  X m();
}

class Ambiguity1 {

  static void m(I1 i1) {}
  static <T> void m(I2<T> i2) {}

  {
    m<error descr="Ambiguous method call: both 'Ambiguity1.m(I1)' and 'Ambiguity1.m(I2<Object>)' match">(()->{throw new AssertionError();})</error>;
    m(() -> {});
    m(() -> {
        if (false) return;
        throw new RuntimeException();
    });
  }
}
