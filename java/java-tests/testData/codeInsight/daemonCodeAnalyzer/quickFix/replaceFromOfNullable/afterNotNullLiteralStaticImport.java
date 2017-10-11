// "Replace with '.of()'" "true"
import java.util.Optional;

import static java.util.Optional.ofNullable;

class A{
  void test(){
    Optional.of(11);
  }
}