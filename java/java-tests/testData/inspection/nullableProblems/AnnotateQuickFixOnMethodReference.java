import org.jetbrains.annotations.NotNull;

class Test {

  public I getI() {
    return this::<warning descr="Not annotated method is used as an override for a method annotated with NotNull">getMy<caret>String</warning>;
  }

  String getMyString() {
    return toString();
  }
}

interface I {
  @NotNull
  String getString();
}