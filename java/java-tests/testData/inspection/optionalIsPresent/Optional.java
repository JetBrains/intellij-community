import java.util.Optional;

class OptionalIsPresent {
  public void foo(Optional<String> str) {
    String val;
    if (<warning descr="Can be replaced with single expression in functional style">str.isEmpty()</warning>) {
      val = "";
    } else {
      val = str.get();
    }
    System.out.println(val);
  }
}
