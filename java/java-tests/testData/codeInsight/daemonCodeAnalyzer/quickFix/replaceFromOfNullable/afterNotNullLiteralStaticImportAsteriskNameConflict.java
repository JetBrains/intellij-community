// "Replace with '.of()'" "true-preview"
import java.util.Optional;

import static java.util.Optional.*;

class A{
  void of() {}

  void test(){
    Optional.of(11);
  }
}