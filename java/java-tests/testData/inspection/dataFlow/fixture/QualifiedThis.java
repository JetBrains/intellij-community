import org.jetbrains.annotations.Nullable;

class WithInner extends SomeSuper {
  @Nullable Object o;

  {
    new Runnable() {
      @Override
      public void run() {
        if (WithInner.this.o != null) {
          System.out.println(WithInner.this.o.toString());
        }
      }
    };
  }
}

class SomeSuper {
  @Nullable Object o2;
}

class Impl1 extends SomeSuper {
  class Impl2 extends SomeSuper {
    {
      new Runnable() {
        @Override
        public void run() {
          if (Impl1.this.o2 != null) {
            System.out.println(Impl2.this.o2.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
          }
        }
      };

      SomeSuper s = Impl1.this;
      if (s.o2 != null) {
        System.out.println(s.o2.toString());
      }
    }
  }
}