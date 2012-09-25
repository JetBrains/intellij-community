import org.jetbrains.annotations.Nullable;

import java.lang.String;

public class BrokenAlignment {

  void main(Data data) {
    if (data.getText() != null) {
      System.out.println(data.getText().hashCode());
    }

    data = new Data(null, <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
    System.out.println(<warning descr="Method invocation 'data.getText().hashCode()' may produce 'java.lang.NullPointerException'">data.getText().hashCode()</warning>);

    if (data.inner() != null) {
      System.out.println(data.inner().hashCode());
      System.out.println(<warning descr="Method invocation 'data.inner().getText().hashCode()' may produce 'java.lang.NullPointerException'">data.inner().getText().hashCode()</warning>);
      if (data.inner() != null) {
        System.out.println(data.inner().hashCode());
      }

      data = new Data(null, <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
      System.out.println(<warning descr="Method invocation 'data.inner().hashCode()' may produce 'java.lang.NullPointerException'">data.inner().hashCode()</warning>);
    }
  }

  void main2(Data data) {
    if (data.inner() != null && data.inner().getText() != null) {
      System.out.println(data.inner().hashCode());
      System.out.println(data.inner().getText().hashCode());
    }
  }

  void main3(Data data) {
    if (data.innerOverridden() != null) {
      System.out.println(data.innerOverridden().hashCode());
    }
    if (data.something() != null) {
      System.out.println(<warning descr="Method invocation 'data.something().hashCode()' may produce 'java.lang.NullPointerException'">data.something().hashCode()</warning>);
    }
  }

  private static class Data {
    @Nullable final String text;
    @Nullable final Data inner;

    Data(@Nullable String text, Data inner) {
      this.text = text;
      this.inner = inner;
    }

    @Nullable
    public String getText() {
      return text;
    }

    @Nullable
    public Data inner() {
      return inner;
    }

    @Nullable
    public Data innerOverridden() {
      return inner;
    }

    @Nullable
    public String something() {
      return new String();
    }
  }

  class DataImpl extends Data {
    DataImpl(@Nullable String text, Data inner) {
      super(text, inner);
    }

    @Nullable
    @Override
    public Data innerOverridden() {
      return super.innerOverridden();
    }
  }
}