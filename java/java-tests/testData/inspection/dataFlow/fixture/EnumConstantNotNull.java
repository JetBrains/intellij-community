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

  boolean checkValueOf(String name) {
    return <warning descr="Condition 'AnEnum.valueOf(name) != null' is always 'true'">AnEnum.valueOf(name) != null</warning>;
  }

  boolean checkCustomValueOf(int i) {
    return AnEnum.valueOf(i) != null;
  }

  private native void anotherMethod(String name);

  @Nullable
  private native AnEnum nullableGetter();

  enum AnEnum {
    ENUM_VALUE, FOO2;

    @Nullable
    public static AnEnum valueOf(int i) {
      if (i < 0 || i > 1)
        return null;

      return values()[i];
    }
  }
}
