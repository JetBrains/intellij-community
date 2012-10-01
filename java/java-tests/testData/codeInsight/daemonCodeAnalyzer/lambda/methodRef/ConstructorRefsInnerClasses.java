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
    I1 i1 = NonStaticInner.Inner :: new;
    call11(NonStaticInner.Inner :: new);

    <error descr="Incompatible types. Found: '<method reference>', required: 'NonStaticInner.I2'">I2 i2 = NonStaticInner.Inner :: new;</error>
    call12<error descr="'call12(NonStaticInner.I2)' in 'NonStaticInner' cannot be applied to '(<method reference>)'">(NonStaticInner.Inner :: new)</error>;
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
      Inner(StaticInner outer) {}
      Inner() {}
    }

    interface I1 {
      Inner _(StaticInner rec);
    }

    interface I2 {
      Inner _();
    }

    static void call3(I1 s) {}
    static void call3(I2 s) {}

    static {
      call3<error descr="Cannot resolve method 'call3(<method reference>)'">(StaticInner.Inner :: new)</error>;
    }
}