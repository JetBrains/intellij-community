import org.jetbrains.annotations.Nullable;
class BrokenAlignment {

  void main(Data data) {
    if (data.text != null) {
      System.out.println(data.text.hashCode());
    }

    data = new Data(null, <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
    System.out.println(data.text.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());

    if (data.inner != null) {
      System.out.println(data.inner.hashCode());
      System.out.println(data.inner.text.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
      if (<warning descr="Condition 'data.inner != null' is always 'true'">data.inner != null</warning>) {
        System.out.println(data.inner.hashCode());
      }

      data = new Data(null, <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
      System.out.println(data.inner.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>());
    }
  }

  void main2(Data data) {
    if (data.inner != null && data.inner.text != null) {
      System.out.println(data.inner.hashCode());
      System.out.println(data.inner.text.hashCode());
    }
  }

  private static class Data {
    @Nullable public final String text;
    @Nullable public final Data inner;

    Data(@Nullable String text, Data inner) {
      this.text = text;
      this.inner = inner;
    }
  }
}