import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.Object;

class B {
     @NotNull
     B b = new B();

    public B <warning descr="Getter for @NotNull field might be annotated @NotNull itself">getB</warning>() {
        return b;
    }

    public void setB(B <warning descr="Setter parameter for @NotNull field might be annotated @NotNull itself">b</warning>) {
        this.b = b;
    }

        @NotNull
        private String bug = "true";

        public boolean getBug() {
            return Boolean.valueOf(bug);
        }
}
class C {
  @NotNull C c;

  C(C <warning descr="Constructor parameter for @NotNull field might be annotated @NotNull itself">c</warning>) {
    this.c = c;
  }

  C(@Nullable C c, int i) {
    this.c = c;
  }

  @Nullable
  public C <warning descr="Getter for @NotNull field is annotated @Nullable">getC</warning>() {
    return c;
  }

  public void setC(@Nullable C <warning descr="Setter parameter for @NotNull field is annotated @Nullable">c</warning>) {
    this.c = c;
  }

  @NotNull C c1 = new C(null);
  @org.jetbrains.annotations.Nullable
  public C getC1() {
    if (c1 != null) {
      return null;
    }
    return c1;
  }
}

class D {
    @Nullable Long myL;

    D(long l) {
      myL = l;
    }
}

class E {
  final @NotNull C c;

  E(C <warning descr="Constructor parameter for @NotNull field might be annotated @NotNull itself">c</warning>) {
    this.c = c;
  }

}
class F {
  @Nullable Object field;

  public void setField(@NotNull Object field) {
    this.field = field;
  }
}