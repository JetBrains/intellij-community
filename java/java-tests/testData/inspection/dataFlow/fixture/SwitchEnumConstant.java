import org.jetbrains.annotations.*;

class InspectionTest {

  enum Type {
    PUBLIC, PRIVATE
  }

  @Nullable
  public static String foo(Type type) {
    Object obj = null;
    if (type == Type.PUBLIC) {
      obj = new Object();
    }

    switch (type) {
      case PUBLIC:
        return test(obj);
      case PRIVATE:
      default:
        return null;
    }
  }

  public static String test(@NotNull Object a) {
    return a.toString();
  }
}