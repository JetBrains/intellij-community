import java.util.Set;

class Test {
  void foo(Set<String> outer, Set<String> inner) {
    outer.stream().map((String f)-> {
      inner.stream().filter(
        (String s)->{return true;}
      );
    <error descr="Missing return statement">}</error>);
  }
}