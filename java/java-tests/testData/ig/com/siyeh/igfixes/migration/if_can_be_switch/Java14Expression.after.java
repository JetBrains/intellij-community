import java.util.Objects;

class ObjectsEquals {
  int objectsEquals(String param) {
      <caret>return switch (param) {
          case "a" -> 1;
          case "b" -> 2;
          default -> 3;
      };
  }
}