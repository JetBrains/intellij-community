import java.util.Objects;

class ObjectsEquals {
  int objectsEquals(String param) {
    if<caret> (Objects.equals(param, "a")) {
      return 1;
    } else if (Objects.equals(param, "b")) {
      return 2;
    } else {
      return 3;
    }
  }
}