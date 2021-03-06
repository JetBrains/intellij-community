class Container<MyContainedType> {
  static abstract class GoodState<GoodStateType> {
    void f(Class<GoodStateType> c) {}
  }

  class Bar {}
  void f(GoodState<Bar> g) {
    g.f<error descr="'f(java.lang.Class<Container.Bar>)' in 'Container.GoodState' cannot be applied to '(java.lang.Class<Container.Bar>)'">(Bar.class)</error>;
  }
  
  static class Bar1 {}
  void f1(GoodState<Bar1> g) {
    g.f(Bar1.class);
  }

  void withLocalClass() {
    class BarLocal {
      void f(GoodState<BarLocal> g) {
        g.f<error descr="'f(java.lang.Class<BarLocal>)' in 'Container.GoodState' cannot be applied to '(java.lang.Class<BarLocal>)'">(BarLocal.class)</error>;
      }
    }
  }
}
