// "Replace with lambda" "true"
import org.jetbrains.annotations.Nullable;

interface Bar {}
interface Foo {
  @Nullable
  String foo(Bar bar);
}

class X {
  Foo test() {
    return new <caret>Foo() {
      @Nullable
      @Override
      public String foo(Bar bar) {return null;}
    };
  }
}