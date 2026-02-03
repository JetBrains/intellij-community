import java.util.function.Consumer;
class Foo {
  private static Consumer<Object> consumer = Foo::vaMethod;

  private static <T> T vaMethod(Object... varargs) {
    return null;
  }
}