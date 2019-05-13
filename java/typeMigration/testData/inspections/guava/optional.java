import com.google.common.base.Optional;

class A {

  void m() {
    Optional<String> <caret>o = Optional.absent();
    String s = o.get();
    Optional<String> yetAnotherOptional = Optional.fromNullable("bla-bla");
    System.out.println(o.or(yetAnotherOptional));
    if (o.isPresent()) {
      System.out.println(o.get());
    }
    System.out.println(o.orNull());
    Set<String> set = o.asSet();
  }

}