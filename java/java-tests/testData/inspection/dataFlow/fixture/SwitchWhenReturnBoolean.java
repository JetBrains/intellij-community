public class SwitchWhenReturnBoolean {
  sealed interface Parent {}

  record Child(int x) implements Parent {}

  private static boolean test(Parent id) {
    return switch (id) {
      case Child c when c.x == 0 -> true;
      default -> false;
    };
  }
}