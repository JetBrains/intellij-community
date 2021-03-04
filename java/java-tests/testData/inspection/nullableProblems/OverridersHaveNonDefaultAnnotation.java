import org.eclipse.jdt.annotation.NonNull;

abstract class Foo {

  abstract void foo(<warning descr="Overridden method parameters are not annotated">@<caret>NonNull</warning> String str);


  public static class Foo2 extends Foo {

    @Override
    void foo(@NonNull String str) {
    }
  }

  public static class Foo3 extends Foo {

    @Override
    void foo(String <warning descr="Not annotated parameter overrides @NonNull parameter">str</warning>) {
    }
  }
}