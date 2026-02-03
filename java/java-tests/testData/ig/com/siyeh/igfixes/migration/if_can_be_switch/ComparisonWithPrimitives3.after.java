import org.jetbrains.annotations.NotNull;

class Test {
    String test(int code) {
        <caret>return switch (code) {
            case 100 -> "Continue";
            case 200 -> "OK";
            case 301 -> "Moved permanently";
            case int i when i > 502 && i < 600 -> "Server error";
            default -> "unknown code";
        };
    }
}