import java.util.Optional;

class OptionalIsPresent_2 {
  public void foo() {
    Optional<Object> opt = Optional.ofNullable(Math.random() > 0.5 ? new Object() : null);
    if (<warning descr="Can be replaced with single expression in functional style">opt.isPresent()</warning>) {
      System.out.println(opt.get());
    }
  }
}
