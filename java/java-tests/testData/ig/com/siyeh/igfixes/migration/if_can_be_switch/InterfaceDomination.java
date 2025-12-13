import org.jetbrains.annotations.NotNull;

class Test {
    interface A{}
    interface B extends A{}
    String test(@NotNull Object a) {
      i<caret>f (code instanceof A) {
        return "Continue";
      } else if (code instanceof B) {
        return "OK";
      } else {
        return "unknown code";
      }
    }
}