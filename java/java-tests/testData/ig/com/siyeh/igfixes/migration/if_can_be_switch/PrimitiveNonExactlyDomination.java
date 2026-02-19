import org.jetbrains.annotations.NotNull;

class Test {
  String test(long code) {
      i<caret>f (code instanceof int) {
        return "Continue";
      } else if (code == 1) {
        return "OK";
      } else {
        return "unknown code";
      }
    }
}