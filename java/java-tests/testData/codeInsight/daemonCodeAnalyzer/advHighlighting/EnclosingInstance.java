// no enclosing instance when inheriting inner class


class A57 {
    class X  {
    }
    void f() {
        class XL extends A57.X {
        }

    }
    class XXL extends A57.X {
    }
}
class B57 {
    class X extends <error descr="No enclosing instance of type 'A57' is in scope">A57.X</error> {
    }
}
class C57 extends <error descr="No enclosing instance of type 'B57' is in scope">B57.X</error> {
}


class inner_holder {
  class inner {}
}

class C1_57 extends inner_holder {
  // inner instance available through inheritance
  protected class c extends inner { 
    private class iii extends inner {}
  }
}

/////////////////////////////////////
class ParentA {
    class InnerA {

    }
}

class ParentB extends ParentA {
    static class InnerB extends <error descr="No enclosing instance of type 'ParentA' is in scope">InnerA</error> {

    }
    class InnerC extends InnerA {

    }
}
////////////////////////
class c {
    static class s {
        void f() {
            Object o = this;
        }
    }
    void f() {}
}

class cc {
    static class sc extends c {
        void f() {
            super.f();
        }
    }
}
///////////////////////////
class A
{
  class B
  {
      class C{}
  }
}
class AB extends A.B {
  AB(A a) {
    a.super();
  }
  AB() {
    this(new A());
  }
}
class ABIllegal extends <error descr="No enclosing instance of type 'A' is in scope">A.B</error> {
  ABIllegal(A a) {
  }
  ABIllegal() {
    this(new A());
  }
}


class A42 {
  class B {}
}

class D42 extends A42.B {
  D42() {new A42().super();}
}

class IDEA105004 {
  class A {}
  static class B extends <error descr="No enclosing instance of type 'IDEA105004' is in scope">A</error> {}
}
