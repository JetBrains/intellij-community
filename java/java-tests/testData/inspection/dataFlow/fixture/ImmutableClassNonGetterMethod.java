@javax.annotation.concurrent.Immutable
interface Intf {
  @org.jetbrains.annotations.Nullable String foo();
}

@com.google.auto.value.AutoValue
abstract class Value {
  abstract @org.jetbrains.annotations.Nullable String foo();
}

class Usage {
  void bar(Intf i) {
    if (i.foo() != null) {
      System.out.println(i.foo().length());
    }
  }
  void bar(Value i) {
    if (i.foo() != null) {
      System.out.println(i.foo().length());
    }
  }

}