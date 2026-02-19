import java.util.Optional;

class OptionalIsPresent {
  public void testOptional(Optional<String> opt) {
    if (opt == null) opt = Optional.empty();
    if (opt.isPresent()) {
      // Changing type of `obj` from Object to String will cause a compile-time error on instanceof. Hence, don't suggest fix.
      Object obj = opt.get();
      use(obj instanceof Integer ? "foo" : "bar");
    }
  }

  void use(String obj) { System.out.println("String"); }
}
