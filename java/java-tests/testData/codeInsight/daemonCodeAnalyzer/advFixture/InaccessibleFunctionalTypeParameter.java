package p;
import test.*;

class Test {
  {
    I i = <error descr="'test.A' is not public in 'test'. Cannot be accessed from outside package">(a) -> {}</error>;
    J j = <error descr="'test.A' is not public in 'test'. Cannot be accessed from outside package">() -> null</error>;
  }
}