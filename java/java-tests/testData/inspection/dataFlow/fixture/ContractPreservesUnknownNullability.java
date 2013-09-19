import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class Foo {

  @Contract("null->null")
  String foo(String s){
    return s;
  }

  void bar(String s, String s2) {
    foo(s);
    s.hashCode();
    goo(foo(s2));
  }
  
  void goo(@NotNull String s) {}

}