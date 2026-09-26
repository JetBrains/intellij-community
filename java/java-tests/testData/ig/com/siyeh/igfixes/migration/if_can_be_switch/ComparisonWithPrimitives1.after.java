import org.jetbrains.annotations.NotNull;

class Test {
  String test(@NotNull Integer code) {
      <caret>return switch (code) {
          case 100 -> "Continue";
          case 200 -> "OK";
          case 301 -> "Moved permanently";
          case Integer i when 502 > i -> "Server error";
          default -> "unknown code";
      };
    }
}