import org.jetbrains.annotations.Nullable;

class FooWithComments {
  void unimportantMethod() {
    AnEnum ae = nullableGetter();
    if (ae != null) {
      if (ae == AnEnum.ENUM_VALUE) {
        anotherMethod(ae.name());       // IDEA warns that ae.name() could cause NPE
      } else {
        // do something else
      }
    }
  }

  private native void anotherMethod(String name);

  @Nullable
  private native AnEnum nullableGetter();

  enum AnEnum {
    ENUM_VALUE, FOO2
  }
}
