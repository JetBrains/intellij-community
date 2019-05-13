import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Nullable
  @Contract ( "_ -> null")
  String foo(String s) {
    return <warning descr="Contract clause '_ -> null' is violated">"42"</warning>;
  }

}
