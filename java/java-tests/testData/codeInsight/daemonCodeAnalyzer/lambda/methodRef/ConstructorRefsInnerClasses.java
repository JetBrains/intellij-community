class NonStaticInner {
  class Inner {
    Inner(NonStaticInner outer) {}
    Inner() {}
  }

  interface I1 {
    Inner m(NonStaticInner rec);
  }

  interface I2 {
    Inner m();
  }

  static void call11(I1 s) {}
  static void call12(I2 s) {}

  static {
    I1 i1 = <error descr="An enclosing instance of type NonStaticInner is not in scope">NonStaticInner.Inner :: new</error>;
    call11(<error descr="An enclosing instance of type NonStaticInner is not in scope">NonStaticInner.Inner :: new</error>);

    I2 i2 = <error descr="An enclosing instance of type NonStaticInner is not in scope">NonStaticInner.Inner :: new</error>;
    call12(<error descr="An enclosing instance of type NonStaticInner is not in scope">NonStaticInner.Inner :: new</error>);
  }
}

class StaticInner {

  static class Inner {
    Inner(StaticInner outer) {}
    Inner() {}
  }


  interface I1 {
    Inner m(StaticInner rec);
  }

  interface I2 {
    Inner m();
  }

  static void call21(I1 s) {}
  static void call22(I2 s) {}


  static {
      I1 i1 = StaticInner.Inner :: new;
      call21(StaticInner.Inner :: new);

      I2 i2 = StaticInner.Inner :: new;
      call22(StaticInner.Inner :: new);
  }
}

class StaticInner1 {
    static class Inner {
      Inner(StaticInner1 outer) {}
      Inner() {}
    }

    interface I1 {
      Inner _(StaticInner1 rec);
    }

    interface I2 {
      Inner _();
    }

    static void call3(I1 s) {}
    static void call3(I2 s) {}

    static {
      call3<error descr="Ambiguous method call: both 'StaticInner1.call3(I1)' and 'StaticInner1.call3(I2)' match">(StaticInner1.Inner :: new)</error>;
    }
}

class StaticInner2 {

  static class Inner {
    Inner() {}
  }


  interface I1 {
    Inner m(StaticInner2 rec);
  }


  static {
     I1 i1 = StaticInner2.Inner :: <error descr="Cannot resolve constructor 'Inner'">new</error>;
  }

  {
     I1 i1 = StaticInner2.Inner :: <error descr="Cannot resolve constructor 'Inner'">new</error>;
  }
}

class NonStaticInner2 {

  class Inner {
    Inner() {}
  }


  interface I1 {
    Inner m(NonStaticInner2 rec);
  }


  static {
     I1 i1 = <error descr="An enclosing instance of type NonStaticInner2 is not in scope">NonStaticInner2.Inner :: new</error>;
  }

  {
     I1 i1 = NonStaticInner2.Inner :: <error descr="Cannot resolve constructor 'Inner'">new</error>;
  }
}

class NonStaticInner3 {
    class Foo {
        Foo(Integer i) {}
        Foo() {}
    }

    interface I1<X> {
        X m(int i);
    }

    interface I2<X> {
        X m();
    }
    
    interface I3<X> {
        X m(NonStaticInner3 rec, int i);
    }

    interface I4<X> {
        X m(NonStaticInner3 rec);
    }

    {
        I1<Foo> b1 = Foo::new;
        I2<Foo> b2 = Foo::new;
    }

    {
        I3<Foo> b1 = Foo::<error descr="Cannot resolve constructor 'Foo'">new</error>;
        I4<Foo> b2 = Foo::<error descr="Cannot resolve constructor 'Foo'">new</error>;
    }
}
