import org.jetbrains.annotations.NotNull;

class B {
     @NotNull
     B b;

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

  C(@<error descr="Cannot resolve symbol 'Nullable'">Nullable</error> C <warning descr="Constructor parameter for @NotNull field might be annotated @NotNull itself">c</warning>, int i) {
    this.c = c;
  }

  @<error descr="Cannot resolve symbol 'Nullable'">Nullable</error>
  public C <warning descr="Getter for @NotNull field might be annotated @NotNull itself">getC</warning>() {
    return c;
  }

  public void setC(@<error descr="Cannot resolve symbol 'Nullable'">Nullable</error> C <warning descr="Setter parameter for @NotNull field might be annotated @NotNull itself">c</warning>) {
    this.c = c;
  }

  @NotNull C c1;
  @org.jetbrains.annotations.Nullable
  public C getC1() {
    if (c1 != null) {
      return null;
    }
    return c1;
  }
}

class D {
    @<error descr="Cannot resolve symbol 'Nullable'">Nullable</error> Long myL;

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