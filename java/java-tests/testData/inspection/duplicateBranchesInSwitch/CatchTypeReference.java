import java.util.List;

public class CatchTypeReference {
  void foo(int i) {
    TypeReference<?> targetType = switch (i) {
      case 2231 -> new TypeReference<List<Boolean>>() {
      };
      default -> new TypeReference<List<String>>() {
      };
    };
  }

  private static class TypeReference<T> {
  }
}