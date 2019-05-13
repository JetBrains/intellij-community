import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

class Foo {

  @Contract("null->null")
  String foo(String s){
    return s;
  }

  void bar(String s, String s2) {
    foo(s);
    s.hashCode();
    goo(foo(s2));
  }

  void bar2(String s, String s2) {
    foo(s);
    if (equals(s2)) {
      s.hashCode();
    }
  }


  void goo(@NotNull String s) {}

}