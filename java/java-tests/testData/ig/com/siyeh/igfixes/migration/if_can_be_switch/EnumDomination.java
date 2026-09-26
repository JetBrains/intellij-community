import org.jetbrains.annotations.NotNull;

class Test {
    enum AB{A, B}
    String test(@NotNull AB code) {
      i<caret>f (code instanceof AB) {
        return "Continue";
      } else if (code == AB.A) {
        return "OK";
      } else {
        return "unknown code";
      }
    }
}