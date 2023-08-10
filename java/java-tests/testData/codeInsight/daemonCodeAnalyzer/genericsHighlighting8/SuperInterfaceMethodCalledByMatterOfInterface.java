interface I {
  default void m() {}

}
interface J extends I {}

class C implements I, J {
  {
    <error descr="Bad type qualifier in default super call: redundant interface I is extended by J">I</error>.super.m();
  }
}
class D implements I {}

class E extends D implements I {
    @Override
    public void m() {
        <error descr="Bad type qualifier in default super call: redundant interface I is extended by D">I</error>.super.m();
    }
}