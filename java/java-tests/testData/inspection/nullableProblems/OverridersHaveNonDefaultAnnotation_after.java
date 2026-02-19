import org.eclipse.jdt.annotation.NonNull;
import org.jetbrains.annotations.NotNull;

abstract class Foo {

  abstract void foo(@<caret>NonNull String str);


  public static class Foo2 extends Foo {

    @Override
    void foo(@NonNull String str) {
    }
  }

  public static class Foo3 extends Foo {

    @Override
    void foo(@NotNull String str) {
    }
  }
}