import org.jetbrains.annotations.NotNull;

class Test {
  String test(@NotNull Integer code) {
      i<caret>f (code == 100) {
        return "Continue";
      } else if (code == 200) {
        return "OK";
      } else if (301 == code) {
        return "Moved permanently";
      } else if (502 > code) {
        return "Server error";
      } else {
        return "unknown code";
      }
    }
}