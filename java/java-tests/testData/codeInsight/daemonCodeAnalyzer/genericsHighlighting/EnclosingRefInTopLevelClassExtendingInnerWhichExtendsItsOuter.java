class Outer {
  class Inner extends Outer {}
}

class Impl extends <error descr="No enclosing instance of type 'Outer' is in scope">Outer.Inner</error> {}

class Impl1 {
  class InnerImpl extends <error descr="No enclosing instance of type 'Outer' is in scope">Outer.Inner</error> {}
}

class Impl2 extends Outer {
  {
    class <warning descr="Local class 'L' is never used">L</warning> extends Outer.Inner {}
  }

  class In extends Outer.Inner {}

  static class In1 extends <error descr="No enclosing instance of type 'Outer' is in scope">Outer.Inner</error> {}
}