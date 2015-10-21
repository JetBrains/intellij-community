import com.google.common.base.Optional;

import java.util.Collections;

class A {

  void m() {
    java.util.Optional<String> o = java.util.Optional.empty();
    String s = o.get();
    Optional<String> yetAnotherOptional = Optional.fromNullable("bla-bla");
    System.out.println(java.util.Optional.ofNullable(o.orElseGet(yetAnotherOptional::get)));
    if (o.isPresent()) {
      System.out.println(o.get());
    }
    System.out.println(o.orElse(null));
    Set<String> set = o.isPresent() ? Collections.singleton(o.get()) : Collections.emptySet();
  }

}