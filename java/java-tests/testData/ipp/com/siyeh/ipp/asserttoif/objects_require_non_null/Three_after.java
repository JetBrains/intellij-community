package objects_require_non_null;

import java.util.Objects;

class Three {
  void a(Integer i) {
      //comment
      System.out.println(Objects.requireNonNull(i));
  }
}