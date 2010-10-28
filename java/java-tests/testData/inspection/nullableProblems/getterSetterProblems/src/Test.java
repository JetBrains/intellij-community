import org.jetbrains.annotations.NotNull;

class B {
     @NotNull
     B b;

    public B getB() {
        return b;
    }

    public void setB(B b) {
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

  C(C c) {
    this.c = c;
  }

  C(@Nullable C c, int i) {
    this.c = c;
  }

  @Nullable
  public C getC() {
    return c;
  }

  public void setC(@Nullable C c) {
    this.c = c;
  }
}