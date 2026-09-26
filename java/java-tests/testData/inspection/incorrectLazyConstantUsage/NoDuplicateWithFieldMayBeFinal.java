class Main {
  private LazyConstant<String> <warning descr="'LazyConstant' field should be 'final'">f</warning> = LazyConstant.of(() -> "Bye");
}
