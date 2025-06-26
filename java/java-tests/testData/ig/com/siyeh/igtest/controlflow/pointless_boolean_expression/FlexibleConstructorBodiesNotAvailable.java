class FlexibleConstructorBodiesNotAvailable {
  boolean sideEffect() {
    System.out.println("hello");
    return Math.random() > 0.5;
  }

  class X {
    X(boolean b) {}
  }

  class Y extends X {
    Y(int i) {
      // side-effect cannot be extracted from super call
      super(sideEffect() && false);
    }

    Y(long l) {
      // side-effect cannot be extracted from super call
      super(false & sideEffect());
    }

    Y(double d) {
      // no side-effect extraction necessary
      super(<warning descr="'sideEffect() && true' can be simplified to 'sideEffect()'">sideEffect() && true</warning>);
    }

    Y(float f) {
      // no side-effect extraction necessary
      super(<warning descr="'false && sideEffect()' can be simplified to 'false'">false && sideEffect()</warning>);
    }
  }
}