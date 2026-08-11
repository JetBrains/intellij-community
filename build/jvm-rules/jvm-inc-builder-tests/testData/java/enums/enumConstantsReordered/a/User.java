public class User {
  E v = E.A;
  int f(E e) {
    return switch (e) {
      case A -> 1;
      case B -> 2;
      case C -> 3;
    };
  }
}
