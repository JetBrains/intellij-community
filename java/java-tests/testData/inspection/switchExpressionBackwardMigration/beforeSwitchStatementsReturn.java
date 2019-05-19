// "Replace with old style 'switch' statement" "true"
import java.util.*;

public class Main {
  void foo(E e) {
    switch<caret> (e) {
      case E1, E2 -> {
        System.out.println("oops");
      }
      default -> {
        System.out.println("impossible");
        return;
      }
    }
  }
}

enum E {
  E1, E2;
}