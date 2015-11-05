import java.util.Optional;

class A {

  java.util.Optional<String> m1() {
    Optional<String> o = Optional.empty();
    String s = o.get();
    return o;
  }

  void w1() {
    java.util.Optional<String> someVar = m1();
    System.out.println(someVar.get());
    System.out.println(someVar.isPresent() ? Collections.singleton(someVar.get()) : Collections.emptySet());
  }

}