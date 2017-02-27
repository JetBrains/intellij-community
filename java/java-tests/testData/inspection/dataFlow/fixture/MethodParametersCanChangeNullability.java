import org.jetbrains.annotations.*;

class Test {

  @Nullable
  String findMessage(@NotNull Element element) {
    while (element != null) {
      if (element.getMessage() != null) {
        return element.getMessage();
      }
      element = element.getParent();
    }
    return null;
  }

  @Nullable
  String findMessageWithNullableParent(@NotNull Element element) {
    while (element != null) {
      if (element.getMessage() != null) {
        return element.getMessage();
      }
      element = element.getNullableParent();
    }
    return null;
  }

  void foo(@NotNull String param) {
    if (equals(3)) {
      param = null;
    }
    if (param != null) {
      System.out.println();
    }
  }

}

interface Element {
  Element getParent();
  @Nullable Element getNullableParent();
  @Nullable String getMessage();
}