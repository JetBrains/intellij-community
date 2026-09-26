import java.util.Optional;

class OptionalIsPresent {
  public void foo() {
    Optional<Object> opt = Optional.ofNullable(Math.random() > 0.5 ? new Object() : null);
    if (<warning descr="Can be replaced with single expression in functional style">opt.isPresent()</warning>) {
      Object obj = opt.get();
      use(obj);
    }
  }

  void use(Object obj) { System.out.println("Object"); }
}
