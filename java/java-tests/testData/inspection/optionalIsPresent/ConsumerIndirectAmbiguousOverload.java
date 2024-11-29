import java.util.Optional;

class OptionalIsPresent {
  public void testOptional(Optional<String> str) {
    if (str == null) str = Optional.empty();
    if (str.isPresent()) {
      Object obj = str.get();
      use(obj, 1); // resolves to use(Object)
    }
  }

  void use(Object obj, int i) { System.out.println("Object"); }

  void use(String obj, int i) { System.out.println("String"); }
}
