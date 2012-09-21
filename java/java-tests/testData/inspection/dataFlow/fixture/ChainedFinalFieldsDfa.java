import org.jetbrains.annotations.Nullable;
public class BrokenAlignment {

  void main(Data data) {
    if (data.text != null) {
      System.out.println(data.text.hashCode());
    }

    data = new Data(null, <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
    System.out.println(<warning descr="Method invocation 'data.text.hashCode()' may produce 'java.lang.NullPointerException'">data.text.hashCode()</warning>);

    if (data.inner != null) {
      System.out.println(data.inner.hashCode());
      System.out.println(<warning descr="Method invocation 'data.inner.text.hashCode()' may produce 'java.lang.NullPointerException'">data.inner.text.hashCode()</warning>);
      if (<warning descr="Condition 'data.inner != null' is always 'true'">data.inner != null</warning>) {
        System.out.println(data.inner.hashCode());
      }

      data = new Data(null, <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
      System.out.println(<warning descr="Method invocation 'data.inner.hashCode()' may produce 'java.lang.NullPointerException'">data.inner.hashCode()</warning>);
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