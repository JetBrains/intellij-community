import java.util.Optional;

class OptionalIsPresent {
  public void foo() {
    Optional<Object> opt = Optional.ofNullable(Math.random() > 0.5 ? new Object() : null);
    if (opt.isPresent()) {
      Object obj = opt.get();
      Object a = opt.get();
      System.out.println(opt.get());
    }
  }
}
