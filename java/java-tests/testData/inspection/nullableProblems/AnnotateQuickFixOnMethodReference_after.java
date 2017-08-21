import org.jetbrains.annotations.NotNull;

class Test {

  public I getI() {
    return this::getMyString;
  }

  @NotNull
  String getMyString() {
    return toString();
  }
}

interface I {
  @NotNull
  String getString();
}