import org.jetbrains.annotations.NotNull;

class Test {
  String test(@NotNull Integer code) {
    int aa = 0;
    i<caret>f (code == 100) {
      return "Continue";
    } else if (code == 200) {
      return "OK";
    } else if (301 == code) {
      return "Moved permanently";
    } else if (301 > code && aa++>0) {
      return "Server error";
    } else {
      return "unknown code";
    }
  }
}