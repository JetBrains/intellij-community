// "Replace with old style 'switch' statement" "true"
import java.util.*;

public class Main {
  void foo(E e) {
      switch (e) {
          case E1, E2:
              System.out.println("oops");
              break;
          default:
              System.out.println("impossible");
              break;
      }
  }
}

enum E {
  E1, E2;
}