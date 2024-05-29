import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Integer code) {
      i<caret>f (code instanceof Integer) {
        return "Continue";
      } else if (code == 100) {
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