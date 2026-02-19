// "Replace with lambda" "true"
import org.jetbrains.annotations.Nullable;

interface Bar {}
interface Foo {
  @Nullable
  String foo(Bar bar);
}

class X {
  Foo test() {
    return bar -> null;
  }
}