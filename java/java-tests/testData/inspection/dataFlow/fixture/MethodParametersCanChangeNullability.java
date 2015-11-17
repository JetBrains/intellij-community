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

}

interface Element {
  Element getParent();
  @Nullable Element getNullableParent();
  @Nullable String getMessage();
}