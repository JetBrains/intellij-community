import org.jetbrains.annotations.NotNull;

class Test {
  String test(Number code) {
      i<caret>f (code == 100) {
        return "Continue";
      } else if (code == 200) {
        return "OK";
      } else if (code == 301) {
        return "Moved permanently";
      } else if (code > 502 && code < 600) {
        return "Server error";
      } else {
        return "unknown code";
      }
    }
}