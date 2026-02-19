// "Replace method reference with lambda" "false"
import java.util.function.BiFunction;

class InlineRef<S> {
  <K, R> void foo(BiFunction<S, K, R> f) {}

  void bar( InlineRef<? extends Descriptor> inlineRef){
    inlineRef.foo(Descriptor::ge<caret>tName);
  }
}

class Descriptor {
  static String getName(Descriptor d1, Descriptor d2) {
    return "name";
  }
}

